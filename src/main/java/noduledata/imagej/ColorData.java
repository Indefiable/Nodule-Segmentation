package noduledata.imagej;

import java.awt.Color;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import java.lang.Math;


import ij.WindowManager;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.EllipseFitter;
import ij.io.SaveDialog;


/**
 * This class handles the analysis of the segmented nodule Rois. 
 * 
 * 
 * @author Brandin Farris
 *
 */
public class ColorData {
	
	//RGB value of the color
	public final int[] COLOR;
	private final double WEAKCIRCTHRESHOLD = .45;
	private final double STRONGCIRCTHRESHOLD = .6;
	private final int LOWAREATHRESHOLD = 300;      // areas below this are likely noise.
	private final int measurements = Measurements.AREA + Measurements.AREA_FRACTION + Measurements.CIRCULARITY;
	protected ImagePlus image;
	private final static int[] WHITE = {255,255,255};
	private double HIGHAREATHRESHOLD; // areas above this are likely two or more nodules.
	
	
	public int numNodules = -1;
	private ResultsTable table;
	
	// areas[][[] is nx2 with n Rois. For roi "i", [i][0] is numNodules in that Roi, [i][1] is total area of ROI.
	private int[][] areas;
	private ShapeRoi[] rois;
	// map is a binary image coloring in all the nodules of this type.
	private ImagePlus map;
	private int[] pixels;
	private int[] singleNodulesIndices; // list of ROI indices thought to be singular nodules 
	private NoduleClump[] noduleClumps;
	private RoiManager manager;
	private double[] circularity; 

	
	/**

	 * constructor method. 
	 * 
	 * @param manager : RoiManager object. It's best practice to not have multiple of this object.
	 * @param color : the color of the class of roi's of interest.
	 * @param colorThreshold : area threashold for a single nodule of this type.
	 * @param im : image we're doing analysis on.
	 */
	public ColorData(RoiManager manager,int[] color, int colorThreshold, ImagePlus im) {
		this.image = im;
		this.table = new ResultsTable();
		this.rois = null;
		this.map = null;
		this.pixels = null;
		this.singleNodulesIndices = null;
		this.noduleClumps = null;
		this.manager = manager;
		this.COLOR = color;
		this.areas = null;
		this.HIGHAREATHRESHOLD = colorThreshold;
		
	}
	
	/**
	 * Returns clump at given index.
	 */
	public NoduleClump getClump(int index) {
		 for ( NoduleClump clump : noduleClumps) {
			 if (clump.index == index) {
				 return clump;
			 }
		 }

		return null;
		 
	}
	
	
	/**
	 * 
	 * @return returns this object's array of clump objects.
	 */
	public NoduleClump[] getClumps() {
		
		if ( this.noduleClumps == null && this.table == null && this.map != null) {
			System.out.println("Error: Must generate data first using .getData(boolean)");
			return null;
		}
		return this.noduleClumps;
	}
	
/**
 * Show's the map using ImageJ API.
 */
	public void showMap() {
		map.flatten().show();
	}
	
	/** Replaces ROI at the given index with new ROI Does nothing if index is invalid.*/
	
	
	public void setRoi(int index, ShapeRoi roi) {
		if (index >= 0 && index < rois.length) {
			rois[index] = roi;
		}
		
		int newArea = roi.getContainedPoints().length;
		areas[index][1] = newArea;
		updatePixels();
		updateMap();
		//updateAreas();
	}//=====================================
	
	
	/**
	 * updates pixels using current Roi's. 
	 */
	private void updatePixels() {
		ArrayList<Integer> newPixels = new ArrayList<>();
		
		for(Roi roi : rois) {
			if(roi.getContainedPoints() == null) {
				continue;
			}
			for(Point p : roi.getContainedPoints()) {
				int index = p.y * this.image.getWidth() + p.x;
				newPixels.add(index);
			}
			
		}
		
		int[] thesePixels = new int[newPixels.size()];
		
		int ii = 0;
		for(int point : newPixels) {
			thesePixels[ii++] = point;
		}
		this.pixels = Arrays.stream(thesePixels).distinct().toArray();
		
	}
	

/**
 * updater method for the array of ROI's	
 * @param rois
 */
	public void setRois(ShapeRoi[] rois) {
		this.rois = rois;
		
	}

	/**
	 * returns array of all rois.
	 * @return
	 */
	public ShapeRoi[] getRois() {
		return this.rois;
	}
	
	
	
	/** returns Roi at given index*/
	public ShapeRoi getRoi(int index) {
		return rois[index];
	}
	
	
	
	/** returns index of given roi or -1 if Roi is not found.*/
	public int getIndex(Roi roi) {
		
		if(rois == null) {
			System.out.println("Error: object currently contains no ROI's. Returning -1.");
			return -1;
		}
		
		for (int ii = 0; ii < rois.length; ii++) {
			if (rois[ii] != null) {
				if(rois[ii].equals(roi)) {
					return ii;
				}
			}
		}
		System.out.println("Roi not found. Returning -1.");
		return -1;
	}
	
	
	
	/**
	 * appends roi object to end of list of Roi objects.
	 */
	/** add given roi to array of Rois*/
	public void addRoi(ShapeRoi roi) {
		ShapeRoi[] newrois = new ShapeRoi[rois.length+1];
		
		for(int ii = 0; ii < rois.length; ii++) {
			newrois[ii] = rois[ii];
		}
		
		newrois[rois.length] = roi;
		this.rois = newrois;
		
		updatePixels();
		
		if(this.areas != null) {
			updateAreas();
		}
		
		this.numNodules++;
	}

	/**
	 * Removes the object at the given index from the noduelClumps array.
	 * @param index1
	 */
	private void removeClump(int index1) {
		
		NoduleClump[] array = this.noduleClumps;
		int index = -1;
		
		for(int ii = 0; ii < array.length; ii++) {
			if(array[ii].index == index1) {
				index = ii;
				break;
			}
		}
		
		
		if (index < 0 || index >= array.length) {
            throw new IndexOutOfBoundsException("Index is out of bounds");
        }

        NoduleClump[] newArray =  new NoduleClump[array.length - 1];

        System.arraycopy(array, 0, newArray, 0, index);

        System.arraycopy(array, index + 1, newArray, index, array.length - index - 1);
        
        this.noduleClumps = newArray;
	}
	

	/**
 * 
 * @param roi removes the given ROI from list of rois.
 */
	public boolean removeRoi(ShapeRoi roi) {
	
		boolean isClump = false;
		int index = -1;
		
		for (int ii = 0; ii < rois.length; ii++) {
			
			if( rois[ii] == roi) {
				index = ii;
				break;
			}
		}
		
		if(index == -1) {
			System.out.println("Error: the given roi is not found.");
			return false;
		}
		
		
		if( noduleClumps != null) {
			
			for( NoduleClump clump : noduleClumps) {
				if(clump.index == index) {
					this.numNodules -= clump.numNodules;
					isClump = true;
					removeClump(index);
					break;
				}
			}
			
			if(!isClump) {
				this.numNodules--;
			}
			
			for( NoduleClump clump : noduleClumps) {
				if( clump.index > index) {
				clump.index -=1;
				}
			}
		
		}
		
		ShapeRoi[] newrois = new ShapeRoi[rois.length-1];
		
		double[] newcirc = new double[rois.length-1];
		
		int c = 0;
		
		for (int ii = 0; ii < rois.length; ii++) {
			if(ii == index && index == rois.length-1) {
				break;
			}
			if(ii == index) {
				ii++;
			}
			newrois[c] = rois[ii];
			
			c++; // teehee
		}
		
		this.rois = newrois;
		if(circularity != null) {
			this.circularity = newcirc;
		}
		updateMap();
		int[] indices = {index};
		
		if( this.table != null) {
			removeTableData(indices);
		}
		if( this.areas != null) {
			removeAreas(indices);
		}
		updatePixels();
		return true;
	}//=====================
	

	/**
	 * removes the data for the given indices.
	 */	
	public void removeArrayOfRois(int[] indices) {
		
		
		if(rois.length != table.size()) {
			throw new IllegalStateException("# of rois not equal to table size.");
		}
		
		for( int ii = indices.length-1; ii > 0; ii--) {
			if (indices[ii] < indices[ii-1]) {
				System.out.println("Indices must be in ascending order.");
				return;
			}
		}
		
		// remove in descending order to not mess up indices of rois[].
		for (int ii = indices.length-1; ii >=0; ii--) {
			int index = indices[ii];
			this.removeRoi(rois[index]);
		}
		if(this.noduleClumps == null) {
			return;
		}
		
		if(this.getClump(indices[0]) != null) {
			
		}
		
	}
	
	
	
	/** Removes elements from array of areas.
	 * 
	 * 
	 * @param indices : array of indices to be removed from areas.*/
	private void removeAreas(int[] indices) {
		
		int[][] newArray = new int[areas.length - indices.length][areas[0].length];
		
		  int newArrayRowIndex = 0;
	        int indicesIndex = 0;

	        
	        for (int i = 0; i < areas.length; i++) {
	            if (indicesIndex < indices.length && i == indices[indicesIndex]) {
	                indicesIndex++; 
	            } else {
	                newArray[newArrayRowIndex] = areas[i];
	                newArrayRowIndex++;
	            }
	        }
		
	        this.areas = newArray;
		}
	
	
	
	/** Removes elements from table array.
	 * @param indices : array of indices to be removed from table.*/
	private void removeTableData(int[] indices) {
		
		for(int ii = indices.length-1; ii >=0; ii--) {
			int index = indices[ii];
			table.deleteRow(index);
		}//for
	}
	

/**
 * Sets or updates the binary map.	
 * @param map
 */
	public void setMap(ImagePlus map) {
		this.map = map;
	}
	



	/** areas[][[] is nx2 with n Rois. For roi "i", [i][0] is numNodules in that Roi, [i][1] is total area of ROI.
	 */
	public int[][] getArea() {
		return areas;
	}
	


	/**
	 * Returns the binary map for this ColorData object.
	 * @return
	 */
	public ImagePlus getMap() {
		return map;
	}
	

/**
 * Sets the ImageJ ResultsTable object. It's best practice
 * to not have more than oen of these objects at a time.	
 * @param table
 */
	public void setTable(ResultsTable table) {
		this.table = table;
		
	}
	

/**
 * Returns the given ResultsTable 	
 * @return
 */
	public ResultsTable getTable() {
		return this.table;
	}
	

/**
 * 	Sets the pixels that are counted as nodule pixels of this type.
 * @param pixels
 */
	public void setPixels(int[] pixels) {
		this.pixels = pixels;
	}
	

/**
 * Returns the array of pixels that are nodule pixels of this type.	
 * @return
 */
	public int[] getPixels() {
		return this.pixels;
	}
	
	
	
/** Creates an annotated version of the map and displays it.*/
	public void showAnnotatedMap() {
		
		if (this.map == null) {
			System.out.println("Error: must first genereate segmentation map.");
			return;
		}
		if(this.areas == null) {
			System.out.println("Error: must first generate data using .getData()");
			return;
		}
		
		ImagePlus annotatedMap = new ImagePlus("annotated", map.getProcessor().getBufferedImage());
		Overlay overlay = new Overlay();
    	overlay.clear();
    	
    	for (Roi roi : this.rois) {
    		if(roi == null) {
    			System.out.println("null ROI found while annotating map.");
    		}
    		// Add labels to the ROI
    		roi.setPosition(0);
    		roi.update(true, false);
    		
    		// Add outlines to the ROI
    		roi.setStrokeColor(Color.white);
    		roi.setStrokeWidth(2);
    		    
    	    overlay.add(roi);   
    	}
    	
    	overlay.setLabelFontSize(30, "bold");
    	overlay.drawLabels(true);
		overlay.drawNames(false);
		overlay.drawBackgrounds(true);
		overlay.setLabelColor(Color.white);
		overlay.setLabelFont(overlay.getLabelFont());
	
		annotatedMap.setOverlay(overlay);
		annotatedMap.flattenStack();
		annotatedMap.show();
	}//=============================
	
	
	
	/** 
	 * fills given rect with given color.
	 * @param rect : rect object to fill in with white
	 * @param ip : imageprocessor object to color onto.
	 * @return : imageprocessor with the the white square filled in.
	 */
	public static ImageProcessor fillRect(Rectangle rect, ImageProcessor ip) {
		
		for( int ii = 0; ii < rect.width; ii++) {
			int x = rect.x + ii;
			for( int jj = 0; jj < rect.height; jj++) {
				int y = rect.y + jj;
				ip.putPixel(x, y, WHITE);
			}
		}
		
		return ip;
	}
	
	

	/** Generates a map given a background image and ROIs.*/
	public static ImagePlus createImageWithRoi(ImagePlus image, Roi[] rois) {
       ImageProcessor ip = image.getProcessor().createProcessor(image.getWidth(), image.getHeight());
       
       ip.setColor(Color.black);
       ip.fill();
       
       ip.setColor(Color.white);
       
       for(Roi roi : rois) {
    	   ip = fillRect(roi.getBounds(), ip);
       }
       
       ColorProcessor bp =  image.getProcessor().convertToColorProcessor();
       
       int[] pixels = (int[]) bp.getPixels();
       int[] newpixels = (int[]) ip.convertToColorProcessor().getPixels();
       
       for(int ii = 0; ii < pixels.length; ii++) {
    	  
    	   if(newpixels[ii] != -16777216) {
    		   newpixels[ii] = pixels[ii];
    	   }
       }
       
       ImagePlus result = new ImagePlus("Rois", new ColorProcessor(image.getWidth(), image.getHeight(),newpixels));
       result.flatten();
       
        return result;
    }
	
	
	
	/** 
	 * finds ROIS in the image and 
	 * 
	 * measures the areas of the rois, removing ones with too small of an 
	 * area, counting them as noise. 
	 * 
	 * TODO: rewrite this to not pop up any windows during computation.
	 */
	private void measure() {
		if(WindowManager.getWindow("New Results") != null) {
			WindowManager.getWindow("New Results").dispose();
		}
		
		manager.setEnabled(true);
		manager.reset();
		
		rois = null;
		table = new ResultsTable();
		ImagePlus binaryMap = convertToBinary(); // intensive
		
		binaryMap.show();
		binaryMap.getProcessor().setAutoThreshold("Default"); //intensive
		Roi roi = ThresholdToSelection.run(binaryMap);        // outline all nodules as one ROI.
		
		
		IJ.run("Set Measurements...", "area area_fraction circularity stack add redirect=None decimal=3");
		
		Analyzer analyzer = new Analyzer(binaryMap, measurements, table);
		
		if( roi == null) {
			System.out.println("No ROI found.");
			return;
		}
		
		manager.add(map,roi,0);  
		
		
		if (manager.getRoisAsArray() == null) {
			System.out.println("No ROI's found for this color");
			return;
		}
		if( manager.getRoi(0) == null) {
			System.out.println("No ROI's found for this color");
			return;
		}
		
		manager.select(0);	 // select all nodules as one ROI
		
		
		if(manager.getRoi(0).getType()== Roi.COMPOSITE) {
			manager.runCommand("split"); 
			int[] temp = {0};			 // selecting roi[0], which is all nodules as one roi.
			manager.setSelectedIndexes(temp);
			manager.runCommand("delete");// deleting that ^^^^
		}
		
		Roi[] tempRois = manager.getRoisAsArray();     // getting all roi's in array
		ShapeRoi[] rois = new ShapeRoi[tempRois.length];
		int ij = 0;
		String color= null;
		
		if(this.COLOR[0] != 0) {
			color="r";
		}
		else if(this.COLOR[1] != 0) {
			color= "g";
		}
		else {
			color = "m";
		}
		
		
		for(Roi troi : tempRois) {
			rois[ij] = new ShapeRoi(troi);
			rois[ij].setName(color + " " + ij);
			ij++;
		}
		
		this.rois = rois;
		
		
		for (int ii = 0; ii < rois.length; ii++) {
			manager.select(ii);
			analyzer.measure(); 	 
		}
		
		
		binaryMap.close();
		
	}
	
	
	
	/**
	 * Method that generates the ResultsTable, ROI's, int[] areas, and numNodules.<br>
	 * Finds all ROIs using the measure method.<br>
	 * Finds any rois with large enough measure, and fills them in. <br>
	 * measures again to account for filled in holes. <br>
	 * removes noise from dataset <br>
	 * splits mixed nodules <br>
	 * 
	 * @param mixed : true if this colordata object is for mixed nodules, false otherwise.
	 */
	public void getData(boolean mixed) {
		//IJ IJ = new IJ();
		
		
		if (this.map.getProcessor() == null) {
			System.out.println("Error: no map found. Generate a map first.");
			return;
		}
		
		/**
		 * for all RoiManager things, 
		 * ij.plugin.frame.RoiManager  line 211
		 * 
		 * 
		 */
		measure();
		
		if(manager.getRoi(0) == null || manager.getRoisAsArray() == null) {
			System.out.println("No Roi's found. Returning empty dataset.");
			int[][]temp = new int[1][2];
			temp[0][0] = 0;
			temp[0][1] = 0;
			this.areas = temp;
			this.table.addRow();
			this.table.addValue(0, 0);
			this.table.addValue(1, 0);
			this.rois = new ShapeRoi[0];
			return;
		}
		
		fillNoduleHoles();

		measure();
		
		this.table.show("New Results");       // ResultsTable results contains all measurements.
		
		this.areas = new int[table.size()][2];
		
		for( int ii = 0; ii < table.size(); ii++) {
			this.areas[ii][0] = 1;
			this.areas[ii][1] = (int) table.getValueAsDouble(0, ii);
		}
		
		cleanData(); 
		
		if (!mixed) {
			splitting();
			updateAreas();
			fillCircularity();
		}
		
		int numNodules = 0;
		for( int ii = 0; ii < areas.length; ii++) {
			numNodules += areas[ii][0];
		}
		manager.reset();
		
		
		this.numNodules = numNodules;
	}//==================================================================================================================
	
	// resultsheading(24) == %Area
	// resultsheading(0) == Area
	// resultsheading(18) == circularity
	// ij.Measure.ResultsTable

	
	// ====================================
/**
 * Finds noisy roi's and removes them from the map, resultsTable, roi array<br>
 * Uses low area threshold to rule out rois that are too small.
 * */
	void cleanData() {
		
		if( rois.length != table.size()) {
			throw new IllegalStateException("# rois: " + rois.length + ". table data size: " + table.size() + ". Mismatch.");
		}
		
		
		List<Integer> delete = new ArrayList<>();
		
		
		// cycle through index, remove area == 1 ==> single pixel ==> noise.
		for (int ii = 0; ii <table.size(); ii++) {
			
			try {
			int area = Integer.parseInt(table.getStringValue(0, ii));
		
			if ( area < LOWAREATHRESHOLD ) {
				delete.add(ii);
			}
			}catch(Exception e) {
				e.printStackTrace();
			}
			
		}//for
		int[] roisToDelete = delete.stream().mapToInt(Integer::intValue).toArray();
		
		this.removeArrayOfRois(roisToDelete);
		
		System.out.println("number of noisy ROI's: " + roisToDelete.length);
		manager.setSelectedIndexes(roisToDelete);
		manager.runCommand("delete");
	
	}//=========================================

	
	
	
//  ================================
	private void fillCircularity() {
		this.circularity = new double[rois.length];
		for(int ii = 0; ii < rois.length; ii++) {
			circularity[ii] =  table.getValueAsDouble(18, ii);;
		}
	}//===============================
	
	
	
	/**Uses aspect ratio and area to locate and remove fluoresced roots caught in initial segmentation*/
	public void removeRoots() {
		map.show();
		for( int ii = 0; ii < rois.length; ii++) {
			EllipseFitter ellipse = new EllipseFitter();
			this.map.setRoi(rois[ii]);
			ellipse.fit(map.getProcessor(), null);
			double ratio = ellipse.major / ellipse.minor;
			if(rois[ii].getContainedPoints().length > 600) {
				System.out.println(ii + ": "  + calculateAspectRatio(rois[ii].getPolygon())
				+ ", " + table.getValueAsDouble(18, ii) + ", " + ratio);
			}
		}
	}
	
/**
 * Computes the aspect ratio of the given polygon.
 * @return
 */
	private double calculateAspectRatio(Polygon polygon) {
        int numPoints = polygon.npoints;
        double maxSideLength = 0;
        double minSideLength = Double.MAX_VALUE;

        for (int i = 0; i < numPoints; i++) {
            int x1 = polygon.xpoints[i];
            int y1 = polygon.ypoints[i];
            int x2 = polygon.xpoints[(i + 1) % numPoints];
            int y2 = polygon.ypoints[(i + 1) % numPoints];

            double sideLength = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
            if (sideLength > maxSideLength) {
                maxSideLength = sideLength;
            }
            if (sideLength < minSideLength) {
                minSideLength = sideLength;
            }
        }

        return maxSideLength / minSideLength;
    }

	
/**
 * remakes the binary map using the roi array.
 */
	void updateMap() {
		
		ColorProcessor cp = new ColorProcessor(map.getWidth(), map.getHeight());
		cp.setColor(Color.black);
		cp.fill();
		
		for( Roi roi : rois) {
			
			Point[] pix = roi.getContainedPoints();
			
			for(Point pixel : pix) {
				
				cp.putPixel(pixel.x, pixel.y, COLOR);
			}
		}
		this.map = new ImagePlus(map.getTitle(), cp);
		
		
		
	}
	
	
	
/**
 * Finds ROI's that are white space within true nodule ROI's, and merges the two.
 */
	void fillNoduleHoles() {
		ArrayList<Integer> fakeNodules = new ArrayList<>();
		
		
		if(this.rois == null) {
			return;
		}
		if (rois.length != table.size()) {
			System.out.println("ERROR: size mismatch between table data and # of ROIS");
			
		}
		
		int ii = -1;
		while(ii++ < rois.length-1) {
			double percentArea = table.getValueAsDouble(24, ii);
		
			if(percentArea < 50) {// value table 25 is %area :: ij.Measure.ResultsTable
				fakeNodules.add(ii);
			}
		}
		
		int[] roiContainers = new int[fakeNodules.size()];
		Arrays.fill(roiContainers, -1);
		ii = 0;
		
		while(ii++ < fakeNodules.size()-1) {
			Roi roi0 = getRoi(fakeNodules.get(ii));
			Area area0 = new Area(roi0.getPolygon());
			for(Roi roi2 : rois) {
				Area area2 = new Area(roi2.getPolygon());
				area0.intersect(area2);
				if (!area0.isEmpty()) {
					roiContainers[ii] = getIndex(roi2);
				}
				area0 = new Area(roi0.getPolygon());
			}
			
			if(roiContainers[ii] == -1) {
				System.out.println("Error: container not found for given white space ROI.");
			}
		}
		
		
		ColorProcessor cp = map.getProcessor().convertToColorProcessor();
		
		// copying int[] pixels into newpixels
		ArrayList<Integer> newpixels = new ArrayList<>(Arrays.asList(Arrays.stream(pixels).boxed().toArray(Integer[]::new)));
		
		
		ii = 0;
		while ( ii++ < fakeNodules.size()-1) {
			Roi roi = getRoi(fakeNodules.get(ii));
			Point[] points = roi.getContainedPoints();
			
			for( Point point : points) {
				if(point.x ==0 && point.y == 0) {
					System.out.println("Error: point not found.");
				}
				else {
					cp.putPixel(point.x, point.y, COLOR);
					newpixels.add(point.y*map.getWidth() + point.x);
				}
			}
		}
		
		this.pixels = newpixels.stream().mapToInt(Integer::intValue).toArray();
		map = new ImagePlus(map.getTitle(), cp);
	}
	
	
	
/**
 * save data as CSV file.
 */
	public void saveTable() {
		SaveDialog saver = new SaveDialog("Save CSV File", "data.csv", "");
		
		String dir = saver.getDirectory();
		String name = saver.getFileName();
		
		if ( dir == null || name == null) {
			IJ.log("saving cancelled");
			return;
		}
		
		String save = dir + name;
		
		table.save(save);
	}
	
	
	
/**
 *converts map to binary map.
 */
	public ImagePlus convertToBinary() {
		if (map == null || pixels == null) {
			System.out.println("Error: must have map and pixels in memory. returning null");
			return null;
		}
		int width = map.getWidth();
		int height = map.getHeight();
		ByteProcessor pc = new ByteProcessor(map.getWidth(), map.getHeight());
		
		for (int ii = 0; ii < height; ++ii) {
			for ( int jj = 0; jj < width; ++jj) {
				pc.putPixel(jj, ii, 255);
			}
		}
		
		
		for (int pix : pixels) {
			int x = pix % width;
			int y = (pix - x) / width;
			pc.putPixel(x, y, 0);
		}
		
		ImagePlus output = new ImagePlus("binary", pc);
		return output;
	}
	

/**
 * Returns the circularity of the roi at the given index.
 * @param index
 * @return
 */
	public double getCircularity(int index) {
		return this.circularity[index];
	}
	
	
	
	/**
	 * categorizes nodules into clumps and single nodules.
	 */
	 void singleNodules() {
		ArrayList<Integer> contestants = new ArrayList<>();
		
		if( rois.length != table.size()) {
			throw new IllegalStateException("# rois: " + rois.length + ". table data size: " + table.size() + ". Mismatch.");
		}
		
		
		for( int ii = 0; ii < rois.length; ii++) {
			// column 18 is circularity
			double circularity = table.getValueAsDouble(18, ii);
			
			if ((circularity > WEAKCIRCTHRESHOLD && table.getValueAsDouble(0, ii) < HIGHAREATHRESHOLD) || circularity > STRONGCIRCTHRESHOLD) {
				contestants.add(ii);
			}
		}
		
		
		int[] indexes = contestants.stream().mapToInt(Integer::intValue).toArray();
		this.singleNodulesIndices = indexes;
		
	}
	
	
	 
	 /**
	 * calculates average area of the singular nodules.
	 */
	public double averageArea() {
		double area = 0;
		try {
		if( areas == null || areas.length == 0) {
			return 1;
		}
		}catch(Exception e) {}
		
		if (singleNodulesIndices ==null) {
			singleNodules();
		}
		
		System.out.println("# of singular nodules: " + singleNodulesIndices.length);
		
		for( int index : singleNodulesIndices) {
			area += table.getValueAsDouble(0, index);
		}
		area = area / singleNodulesIndices.length;
		System.out.println("average area of singular nodules: " + area);
		stDev(area);
		return area;
		
	}

	/**
	 * updates the pixel area of the roi at the given index.
	 * @param index : index of roi to update.
	 * @param area : pixel area we're updating.
	 */
	void updateArea(int index, int area) {
		this.areas[index][1] = area;
	}
	
	
	
	/**
	 * standard deviation of the areas of singular nodules.
	 */
	int stDev(double average) {
		double stdev=0;
		
		for ( int index : singleNodulesIndices) {
			double area = table.getValueAsDouble(0, index);
			
			stdev += ((average - area) * (average - area));
		}
		stdev = stdev / singleNodulesIndices.length;
		stdev =  Math.sqrt( (double) stdev);
		
		return (int) stdev;
	}
	
	
	
	/**
	 * splits the non-singular nodules into NoduleClumps,
	 * assigning the number of nodules and their area
	 * using the average size of the given color in this image.
	 */
	private void splitting(){
		
		double avg = averageArea();
		if(avg == 1) {
			System.out.println("No average found. No nodules were split.");
			return;
		}
		
		ArrayList<Integer> indices = new ArrayList<>();
		ArrayList<NoduleClump> split = new ArrayList<>();
		
		for(int ii = 0; ii < rois.length;ii++) {
			for(int jj = 0; jj < singleNodulesIndices.length; jj++) {
				if (ii == singleNodulesIndices[jj]) {
					break;
				}
				if(jj == singleNodulesIndices.length-1 && table.getValueAsDouble(0, ii)> HIGHAREATHRESHOLD) {
					indices.add(ii);
				}
			}
		}
		System.out.println("# of clumps: " + indices.size());
		System.out.println("indices: # of nodules: area/nodule");
		
		for(int index : indices) {
			double area = table.getValueAsDouble(0, index);
			double nods = area / avg - 1;
			
			if(nods < 2) {
				nods = 2;
			}
			else {
				nods =(int) roundDown(nods);
			}
			
			System.out.println(index  + ": " + nods + ": " + (area/nods));
			split.add(new NoduleClump(index, getRoi(index),(area/nods),(int) nods, this.image));
		}
		
		NoduleClump[] splits = new NoduleClump[split.size()];
		
		for( int ii = 0; ii < split.size(); ii++) {
			splits[ii] = split.get(ii);
		}
		
		this.noduleClumps = splits;
	}
	
	
	
/**
 * updates table after splitting nodule clumps.
 */
	private void updateAreas() {
		
		int[][] newareas = new int[rois.length][2];
		
		for(int ii = 0; ii < rois.length; ii++) {
			
			int area = this.getRoi(ii).getContainedPoints().length;
			int index = isSplit(ii);
			
			if (index != -1) {	
				newareas[ii][0] = noduleClumps[index].numNodules;
				newareas[ii][1] = (int) noduleClumps[index].area;
			}	
			else {
				newareas[ii][0] = 1;
				newareas[ii][1] = area;
			}
			
		}
		
		this.areas = newareas;
	}
	
	
	
	// rounding .7 and below down, rest up.
	int roundDown(double num) {
		
		int truncated = (int) (Math.floor(num * 10) % 10);
		
		if ( truncated < 8 ) {
			return (int) num;
		}
		else {
			return (int) Math.round(num);
		}
		
	}//=========================================
	
	
	
//  =================================
	/**
	 * returns index if given nodule index is split, -1 otherwise.
	 */
	private int isSplit(int ii) {
		if(noduleClumps == null) {
			return -1;
		}
		int[] splitIndices = new int[noduleClumps.length];
	
		int cc = 0;
		
		for ( NoduleClump clump : noduleClumps) {
			splitIndices[cc] = clump.index;
			//System.out.println(cc + ": " + clump.index);
			cc++;
		}
		
		for (int kk = 0; kk < splitIndices.length;kk++) {
			int index = splitIndices[kk];
			if (ii == index) {
				return kk;
			}
			
		}
		return -1;
	}//==============================

	
	
	
	/**
	 * Shows the RG color distribution graph for the given Roi
	 * @param im : a copy of the original image
	 * @param roi : roi to graph
	 * @param boxRoi : if true, will graph the pixels around the roi as well. 
	 * If false, will only graph the pixels within the roi.
	 */
	public void VisualizeRoi(ImagePlus im, Roi roi, boolean boxRoi) {
		
		Roi[] rois = {roi};
		
		im.crop(rois)[0].show();
		ArrayList<Double> redValues = new ArrayList<>();
		ArrayList<Double> greenValues = new ArrayList<>();
		
		if(boxRoi) {
		Rectangle box = roi.getBounds();
		for(int y = (int) box.getMinY(); y < box.getMaxY(); y++) {
			for(int x = (int) box.getMinX(); x < box.getMaxX(); x++) {
				redValues.add( (double) im.getPixel(x, y)[0] );
				greenValues.add((double) im.getPixel(x, y)[1] );
			}
		}
		}
		
		if(!boxRoi) {
			for(Point p : roi) {
				redValues.add( (double) im.getPixel(p.x, p.y)[0] );
				greenValues.add((double) im.getPixel(p.x, p.y)[1] );
			}
		}
		
		
		
		
		Plot plot = new Plot("cluster distribution","Red","Green");
		plot.setAxisXLog(false);
		plot.setAxisYLog(false);
		plot.setLimits(0, 255, 0, 255);
		plot.setColor(Color.BLUE);
		plot.addPoints(redValues, greenValues, Plot.CIRCLE);
		
		plot.setColor(Color.GREEN);
		int[] topLine = Line(.65,25);
		plot.drawLine(topLine[0], topLine[1], topLine[2], topLine[3]);
		ImageProcessor plotImageProcessor = plot.getProcessor();
		ImagePlus image = new ImagePlus("Color Distribution", plotImageProcessor);
		image.show();
	}
	
//  ==========================================
/**
 * 
 * @param m
 * @param b
 * @return int line[4] integer points that make a line with given slope and y-intercept
 */
	private int[] Line(double m, double b) {
		int[] line = new int[4];
		line[1] = (int) b;
		line[0] = 0;
		line[2] = 255;
		line[3] = (int) (255*m + b);
		return line;
	}//======================================
	
	
	
}