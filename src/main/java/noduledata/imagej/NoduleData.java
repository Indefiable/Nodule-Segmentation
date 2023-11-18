package noduledata.imagej;


import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.StringJoiner;

import javax.swing.JFileChooser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.Math;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.io.FilenameUtils;

import ij.gui.ImageCanvas;
import ij.gui.Overlay;
import ij.IJ;
import ij.gui.Plot;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.ColorProcessor;
import ij.process.ByteProcessor;
import ij.process.ColorSpaceConverter;
import ij.process.FloatProcessor;    
import ij.gui.Roi;								   
import ij.io.FileSaver;
import ij.WindowManager;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.frame.RoiManager;		
import ij.measure.ResultsTable;
import ij.process.ImageConverter;


import weka.core.Attribute;
import weka.core.Instances;
import trainableSegmentation.FeatureStackArray;
import trainableSegmentation.unsupervised.ColorClustering;
import trainableSegmentation.unsupervised.ColorClustering.Channel;

/**
 * 
 * This class manages the segmentation of the image and the saving of the data. 
 * Also includes many methods used in testing that are not currently part of the pipeline, but which I kept 
 * in case others find it useful or applicable to improving the program.
 *
 *
 * @author Brandin Farris
 *
 */

public class NoduleData {
	
	
	private final int LIGHT_SECTION_ONE = 0;
	private final int LIGHT_SECTION_TWO = 1;
	private final int LIGHT_SECTION_THREE = 2;
	private final int LIGHT_SECTION_FOUR = 3;
	
	private int GREEN_LIGHT_SECTION;
	
	private final int[] RED = {255,0,0};        
	private final int[] GREEN = {0,255,0}; 		  
	private final int[] YELLOW = {255,255,0};  
	private final int[] BLUE = {0,0,255};	
	private final int[] BLACK = {0,0,0};
	
	
	protected final static double BOTTOMA= .002;   
	protected final static double BOTTOMB = 45;
	protected final static double TOPCUTOFFSLOPE = 1;
	protected final static double TOPB = 25; 
	private final double RLINE = 180;
	private final double GLINE = 240;
	private final double initialSegmentationSlope = -1.58;
	private final double initialSegmentationB = 195;
	
	private ColorData green;
	private ColorData red;
	private MixedData mixed;
	private float averageGreenLightness;
	private float averageRedLightness;
	private RoiManager manager = new RoiManager();
	private Instances instances;      
	private FeatureStackArray fsa;     
	private ColorClustering CCcluster;
	//private HashMap<String, int[]> elatMap = new HashMap<>();
	//private HashMap<String, int[]> fulMap = new HashMap<>();
	//private HashMap<String, int[]> satMap = new HashMap<>();
	
	
	public ImagePlus binarymap = null; // has floatProcessor
	public ImagePlus image;            
	public int numInstances = -1;
	

/** generate a segmentation map using using a ColorClustering model. Initiates 
 * the ColorData objects 
 * @param cluster : cluster object with a loaded image and .model file. 
 * 
 */
//======================================================
	public NoduleData(ColorClustering cluster) throws IllegalStateException {
		
		/**
		elatMap.put("Fix+", new int[] {8000,8000});
		elatMap.put("Mix1", new int[] {3000,8000});
		elatMap.put("Mix2", new int[] {6000,2000});
		elatMap.put("Fix-", new int[] {3000,3000});
		
		fulMap.put("Fix+", new int[] {3000,2000});
		fulMap.put("Mix1", new int[] {2000,2000});
		fulMap.put("Mix2", new int[] {3000,3000});
		fulMap.put("Fix-", new int[] {3000,3000});
		
		satMap.put("Fix+", new int[] {3000,15000});
		satMap.put("Mix1", new int[] {2500,6000});
		satMap.put("Mix2", new int[] {3500,1500});
		satMap.put("Fix-", new int[] {3000,2000});
		**/
		
		
		this.CCcluster = cluster;
		this.image = cluster.getImage();

		cluster.setNumSamples(image.getWidth() * image.getHeight());
		this.fsa = new FeatureStackArray(image.getStackSize());
		
		fsa = cluster.createFSArray(image);
		
		ImagePlus binarymap = cluster.createProbabilityMaps(fsa); // intensive
		
		// overwriting map with new map object that has only one image in the Stack. 
		// originally comes with 2 binary images, one an invert of the other.
		ImageStack mapStack = binarymap.getStack();
		mapStack.deleteSlice(1);
		this.binarymap = new ImagePlus("1d", mapStack.getProcessor(1));
		
		int greenSingleNoduleArea = 0;
		int redSingleNoduleArea = 0;
		
		/**
		if(species.contentEquals("sat")) {
			greenSingleNoduleArea = satMap.get(type)[1];
			redSingleNoduleArea = satMap.get(type)[0];
		}
		else if(species.contentEquals("elat")) {
			greenSingleNoduleArea = elatMap.get(type)[1];
			redSingleNoduleArea = elatMap.get(type)[0];
		}
		else if(species.contentEquals("ful")) {
			greenSingleNoduleArea = fulMap.get(type)[1];
			redSingleNoduleArea = fulMap.get(type)[0];
		}**/
		
		redSingleNoduleArea = 3000;
		greenSingleNoduleArea=3000;
		if(greenSingleNoduleArea == 0 || redSingleNoduleArea == 0) {
			throw new IllegalStateException("illegal species name.");
		}
		
		
		this.green = new ColorData(manager,GREEN, greenSingleNoduleArea, this.image);
		
		this.red = new ColorData(manager, RED, redSingleNoduleArea, this.image);
		
		
		this.mixed = new MixedData(manager,YELLOW, redSingleNoduleArea, this.image);
		
		
		cluster.createFeatures();	// intensive
		
		this.instances = CCcluster.getFeaturesInstances();
		
		
		this.numInstances = instances.numInstances();

		System.out.println("=============================");
		System.out.println(this.image.getTitle());
		System.out.println("=============================");
	}
	
	
//  =======================================================	
	/**
	 * Manually get's the cluster assignments for all pixels in the given image. 
	 * @Param map : binary map for background(white) and nodule pixels(black)
	 * @return : array of cluster assignments(i.e. 0 for background, 1 for foreground)
	 */
     byte[] getAssignments(ImagePlus map) {
		byte[] clusterAssignments = new byte[numInstances];
		
		ByteProcessor test = new ByteProcessor(map.getBufferedImage());
		
		for (int ii = 0; ii < numInstances; ii++){
			clusterAssignments[ii] = (byte) test.get(ii);
		}	
		
		
		return clusterAssignments;
	}//================================================================
     
     
//   ===============================
     /**
      * Uses built in methods to get the assignments of the pixels. 
      * This returns them in a random order.
      * @return : Randomly ordered array of cluster assignments(i.e. 0 for background, 1 for foreground)
      */
     int[] getAssignmentsRandom() {

    	 int[] clusterAssignments = new int[numInstances];
    	
 		for (int ii = 0; ii < numInstances; ii++) {
			try {
				clusterAssignments[ii] = CCcluster.getTheClusterer().clusterInstance(instances.instance(ii));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
 		return clusterAssignments;
     }//==========================
     
     
//  =====================================
     /**
      * Creates a binary map with black being segmented nodules and white being background. 
      */
 	private void generateSegmentedMap() {
 		improveSegmentation();
 		
 		byte[] clusterAssignments = getAssignments(this.binarymap);
 		
 		int[] greenPixelIndices = new int[numInstances];
 		int[] redPixelIndices = new int[numInstances];
 		int[] mixedPixelIndices = new int[numInstances];
 		
 		int numGreenPixels = 0;
 		int numRedPixels = 0;
 		int numMixedPixels = 0;
 		
 		//============================================
 		// generates list of indices of nodule pixel locations w.r.t. clusterAssignments/Instances object.
 		List<Integer> indicesList = new ArrayList<>();
 		for (int ii = 0; ii < numInstances; ii++) {
 			if (clusterAssignments[ii] == 0) { // == 0 ==> segmented nodule pixel.
 				indicesList.add(ii);
 			}
 		}
 		int[] nodulePixelIndices = indicesList.stream().mapToInt(Integer::intValue).toArray();
 		
 		
 		if(averageRedLightness <82) {
 			nodulePixelIndices = improveRedNoduleSegmentation(nodulePixelIndices);
 		}
 		
 		// indices of all the nodule pixels w.r.t. clusterAssignments
 		//===============================================================================
 		
 		
 		//===================================================================================
 		// cycle through all nodule pixels, categorizing them according to segmentation line.
 		for (int ii = 0; ii < nodulePixelIndices.length; ii++) {
 			int loc = nodulePixelIndices[ii];
 			
 			double gValue = image.getPixel(loc % image.getWidth(), (int)(loc)/image.getWidth())[1];
 			double rValue = image.getPixel(loc % image.getWidth(), (int)(loc)/image.getWidth())[0];
 			
 			if (gValue > (rValue * TOPCUTOFFSLOPE + TOPB) || gValue > GLINE) {
 				greenPixelIndices[numGreenPixels] = loc; 
 				numGreenPixels++;
 			}
 			else if (gValue < (rValue * rValue * BOTTOMA + BOTTOMB) || rValue > RLINE) {
 				redPixelIndices[numRedPixels] = loc; 
 				numRedPixels++;
 			}
 			else {
 				mixedPixelIndices[numMixedPixels] = loc;
 				numMixedPixels++;
 			}
 		}//===================================================
 		
 		// resizing the arrays.
 		int[] greenNodsT = new int[numGreenPixels];
 		System.arraycopy(greenPixelIndices, 0, greenNodsT, 0, numGreenPixels);
 		int[] redNodsT = new int[numRedPixels];
 		System.arraycopy(redPixelIndices, 0, redNodsT, 0, numRedPixels);
 		int[] mixedNodsT = new int[numMixedPixels];
 		System.arraycopy(mixedPixelIndices, 0, mixedNodsT, 0, numMixedPixels);
 		
 		
 		green.setPixels(greenNodsT);
 		red.setPixels(redNodsT);
 		mixed.setPixels(mixedNodsT);
 		
 		IJ.log("=========================================");
 		IJ.log("Red Nodule pixels: " + numRedPixels);
 		IJ.log("Green Nodule pixels: " + numGreenPixels);
 		IJ.log("Mixed Nodule pixels: " + numMixedPixels);
 		IJ.log("total nodule pixel count: " + (numGreenPixels + numRedPixels + numMixedPixels));
 		IJ.log("=========================================");
 	
 		
 		red.setMap(createMap(redNodsT,RED));
 		green.setMap(createMap(greenNodsT, GREEN));
 		mixed.setMap(createMap(mixedNodsT, YELLOW));
 	}//=============================================================================
     
 	
//  =========================================
 	/**
 	 * Given a binary classification map, outlines the foreground, creating ROI objects out of each mutually exclusive outline.
 	 * @param newmap : binary classification map.
 	 * @return : array of Roi objects corresponding to outlined nodules.
 	 */
 	private Roi[] getRois(ImagePlus newmap) {
 		
 		manager.setEnabled(true);
    	manager.reset();
    	newmap.show();
    	
    	try {
    		newmap.getProcessor().setAutoThreshold("Default"); //intensive
    	}catch(Exception e) {
    		System.out.println("autothreshold did not work.");
    		e.printStackTrace();
    	}
 		Roi tempRoi = ThresholdToSelection.run(newmap);        // outline all nodules as one ROI.
 		
 		
 		if( tempRoi ==null) {
 			System.out.println("No ROI found.");
				return null;
 		}
 		
 		manager.add(newmap,tempRoi,0); 
 			
 		
 		manager.select(0);			 // select all nodules as one ROI
 		if(manager.getRoi(0).getType()== Roi.COMPOSITE) { // composite ==> spacially separate roi's
			manager.runCommand("split"); // split ROI into pieces.
			int[] temp = {0};			 // selecting roi[0], which is all nodules as one roi.
			manager.setSelectedIndexes(temp);
			manager.runCommand("delete");// deleting that ^^^^
		}
 	
 		Roi[] rois = manager.getRoisAsArray();
 		manager.reset();
 		manager.close();
 		return rois;
 		
 	}
 	
 	
//  =========================================
 	/**
 	 * Helper function for properly displaying Rois on a window.
 	 * @param rois : array of Rois to be displayed in a window.
 	 * @return : array of scaled rectangles for displaying nodules in a window.
 	 */
 	Rectangle[] getScaledBounds(Roi[] rois) {
 		double scale = 10;
 		Rectangle[] roiRects = new Rectangle[rois.length];
 		
 		for(int ii = 0; ii < rois.length; ii++) {
 		roiRects[ii] = rois[ii].getBounds();
 		}
 		
 		Rectangle[] scaledRoiRects = new Rectangle[rois.length];
 		
 		for(int ii = 0; ii < rois.length; ii++) {
 				Rectangle rect = roiRects[ii];
 				int newWidth = (int) (rect.width * scale);
 				int newHeight = (int) ( rect.height * scale);
 				int newX = rect.x - (int) ((newWidth - rect.width) / 2 );
 				int newY = rect.y - (int) ((newHeight - rect.height) / 2 );
 				if(newX < 0) {
 					newX = 0;
 				}
 				if(newY < 0) {
 					newY = 0;
 				}
 				scaledRoiRects[ii] = new Rectangle(newX,newY,newWidth,newHeight);
 		}
 		
 		return scaledRoiRects;
 	}
 	
 	
//  ========================================
 	/**
 	 * Helper function to count the number of segmented green nodules.
 	 * @param rois : array of all segmented nodules.
 	 * @return : boolean array returning true if the roi is green and false otherwise.
 	 */
 	private boolean[] greenRoi(Roi[] rois) {
 		
 		boolean[] greenRois = new boolean[rois.length];
 		
 		for(int ii = 0; ii < rois.length ;ii++) {
 			Roi roi = rois[ii];
 			
 			int numGreen = 0;
 			
 			
 			for(Point p : roi.getContainedPoints()) {
 				double gValue = image.getPixel(p.x, p.y)[1];
 	 			double rValue = image.getPixel(p.x, p.y)[0];
 	 			
 	 			if (gValue > (rValue *rValue* BOTTOMA + BOTTOMB)) {
 	 				numGreen++;
 	 			}
 			}
 			
 			if(numGreen > 50 ) {
 				greenRois[ii] =true;
 			}
 			else {
 				greenRois[ii] = false;
 			}
 		}

 		return greenRois;
 	}
 	
	/**
	 * returns the given image cropped to the given roi. 
	 */
	public static ImagePlus createImageWithRoi(ImagePlus image, Roi  roi) {
		ImagePlus imp = new ImagePlus(image.getShortTitle(), image.getProcessor());
		imp.setRoi(roi);
		return imp.crop();
	}
	
//  ==========================================
	/**
	 * calculates the average lightness of all nodules within the image.
	 * @param rois : array of nodule rois.
	 */
 	public void averageLightness(Roi[] rois) {
 		
 		//Roi[] rois = getRois(new ImagePlus("temp", this.binarymap.getProcessor()));
 		ImageStack channelStack = getChannelGreyscales(new String[] {"L",}, this.image);
 		FloatProcessor light = channelStack.getProcessor(1).convertToFloatProcessor();
 		double tempB = (TOPB + BOTTOMB) / 2;
 		double tempM = .69;
 		int numRedPixels = 0;
 		int numGreenPixels = 0;
 		float redLightness = 0;
 		float greenLightness = 0;
 		for(Roi roi : rois) {
 			
 			for(Point p : roi.getContainedPoints()) {
 				int rValue = this.image.getPixel(p.x, p.y)[0];
 				int gValue = this.image.getPixel(p.x, p.y)[1];
 				if(gValue > (rValue*tempM + tempB)) {
 					numGreenPixels++;
 					greenLightness+=light.getf(p.x, p.y);
 				}
 				else {
 					numRedPixels++;
 					redLightness+=light.getf(p.x, p.y);
 				}
 			}
 			
 		}
 		
 		redLightness = redLightness / numRedPixels;
 		greenLightness = greenLightness / numGreenPixels;
 		
 		if(greenLightness < 85) {
 			this.GREEN_LIGHT_SECTION = this.LIGHT_SECTION_ONE;
 		}
 		else if(greenLightness < 90) {
 			this.GREEN_LIGHT_SECTION = this.LIGHT_SECTION_TWO;
 		}
 		else if(greenLightness < 95) {
 			this.GREEN_LIGHT_SECTION = this.LIGHT_SECTION_THREE;
 		}
 		else {
 			this.GREEN_LIGHT_SECTION = this.LIGHT_SECTION_FOUR;
 		}
 		this.averageGreenLightness = greenLightness;
 		this.averageRedLightness = redLightness;
 		
 		IJ.log("Average green lightness: " + this.averageGreenLightness);
 		IJ.log("Average red lightness: " + this.averageRedLightness);
 		
 	}//=============================================
 	
 	
//   ====================================
 	/**
 	 * Uses various algorithms to catch more nodule pixels after the initial segmentation process. Only activates if certain criteria are met.
 	 * Different algorithms are used to find green and red pixels. 
 	 */
     private void improveSegmentation() {
    	
    	 boolean[] loosenGreenThreshold;
    	 ImagePlus tempMap = new ImagePlus("temp", this.binarymap.getProcessor());
    	 Roi[] rois = getRois(tempMap);
    	 averageLightness(rois);
    	 
    	 if(this.GREEN_LIGHT_SECTION < this.LIGHT_SECTION_TWO) {
    		 loosenGreenThreshold = greenRoi(rois);
    	 }
    	 else {
    		 loosenGreenThreshold = new boolean[rois.length];
    	 }
    	 
    	 Rectangle[] roiRects = getScaledBounds(rois);
    	 if(roiRects == null) {
    		 IJ.log("no ROi's found.");
    		 return;
    	 }
    	
    	int greenThreshold = 80;
    	
 		ByteProcessor byteMap =  binarymap.getProcessor().convertToByteProcessor();
 		ByteProcessor byteImage = this.image.getProcessor().convertToByteProcessor();
 		ImageProcessor coloredMap = binarymap.getProcessor().convertToRGB();
 		boolean adjacent = false;
 		
 		boolean[] sigAddition = new boolean[roiRects.length];
 		
 		for(int kk = 0; kk < roiRects.length; kk++) {
 			Rectangle rect = roiRects[kk];
 			
 			int pink = (255 << 24) | (255 << 16) | (192 << 8) | 203;
 			int red = (255 << 24) | (255 << 16) | (0 << 8) | 0;
 			
 		    //============================
 			if(loosenGreenThreshold[kk]) {
 				
 				switch(this.GREEN_LIGHT_SECTION) {
 				
 				case LIGHT_SECTION_ONE:
 					greenThreshold = 50;
 					break;
 					
 				case LIGHT_SECTION_TWO:
 					greenThreshold = 60;
 					break;
 					
 				case LIGHT_SECTION_THREE:
 					greenThreshold = 70;
 					break;
 					
 				case LIGHT_SECTION_FOUR:
 					greenThreshold = 80;
 					 break;
 				}
 				
 			}//=====================
 			
 			int pixelsAdded = 0;
 			
 		for( int ii = 0; ii < rect.height; ii++) {
 			int y = rect.y +ii;
 			for(int jj = 0; jj < rect.width; jj++) {
 				int x = rect.x + jj;
 				double rValue = image.getPixel(x, y)[0];
 	 			double gValue = image.getPixel(x, y)[1];
 	 			
 				double value = byteImage.getValue(x, y);
 				
 				if(byteMap.getValue(x, y) == 0) {
 					continue;
 				}
 				if(  byteMap.getValue(x+1, y) == 0) {
 					adjacent = true;
 				}
 				if(byteMap.getValue(x-1, y) ==0) {
 					adjacent = true;
 				}
 				if(byteMap.getValue(x, y+1) == 0) {
 					adjacent = true;
 				}
 				if(byteMap.getValue(x, y-1) == 0) {
 					adjacent=true;
 				}
 				
 				if(value > 50 && adjacent) {
 					coloredMap.putPixel(x, y, red);
 					byteMap.putPixelValue(x, y, 0);
 				}
 				
 				else if(rValue > 120 || gValue > greenThreshold) {
 					
 					coloredMap.putPixel(x, y, pink);
 					byteMap.putPixelValue(x, y, 0);
 					pixelsAdded++;
 				}
 				
 				adjacent = false;
 			}
 			
 		}
 		
 		if(pixelsAdded > 1000) {
 			sigAddition[kk] = true;
 		}
 		
 		else {
 			sigAddition[kk] = false;
 		}
 		
 		greenThreshold = 80;
 		
 		}//cycle through rectangles.
 		WindowManager.closeAllWindows();
 		
 		ImagePlus newBinaryMap = new ImagePlus("updated", byteMap);
 		ImagePlus coloredImage = new ImagePlus("Colored", coloredMap);
 		//coloredImage.show();
 		this.binarymap = newBinaryMap;	
 	//	coloredSegmentationMap(sigAddition, rois);
     }//==========================================================
     
     /**
      * creates the binary segmentation map, then adds the pixels added via 
      * improve segmentation to see how much was changed. Used for testing purposes.
      */
     private void coloredSegmentationMap(boolean[] sig, Roi[] rois) {
    	 
    	
    	 ImageProcessor processor = this.binarymap.getProcessor().convertToRGB();

    	 ImagePlus coloredMap = new ImagePlus(this.binarymap.getTitle(),
    			 this.binarymap.getProcessor().convertToRGB());
    	 
    	 
    	 Overlay overlay;
    	 
    	 if(coloredMap.getOverlay() == null) {
    		 overlay = new Overlay();
    		 coloredMap.setOverlay(overlay);
    	 }
    	 else {
    		 overlay = coloredMap.getOverlay();
    	 }
    	 
    	 
    	 for(int ii = 0; ii < sig.length; ii++) {
    		 if(sig[ii] == false) {
    			 continue;
    		 }
    		 
    		 Roi roi = rois[ii];
    		 
    	    roi.setPosition(0);
  		    roi.update(true, false);
  		    roi.setStrokeColor(Color.pink);
  		    roi.setStrokeWidth(5);
  		    overlay.add(roi);
    	 }

		    overlay.drawNames(false);
		    overlay.drawBackgrounds(true);
		    
		    
		    try {
		    coloredMap.flattenStack();
		    }catch(Exception e) {
		    	IJ.log("No ROI's to overlay.");
		    }
		    
    	 coloredMap.show();
    	 
    	// IJ.log("breakpoint");
    	 
    	 
     }
     
     
//   ====================================
     /**
      * saves image to hard-coded path.
      * @param im : image to save.
      */
     private void saveImg(ImagePlus im) {
    	 IJ.saveAs(im, "jpeg","D:\\1EDUCATION\\aRESEARCH\\PlantRoots\\TestingOutput\\" + im.getTitle());
     }
     
     
//   ================================================================
     /**
      * Algorithm to catch more red nodule pixels after the initial segmentation.
      * @param nodulePixels : array of all pixels that are currently segmented nodule pixels.
      * @return : improved array of segmented nodule pixels.
      */
     private int[] improveRedNoduleSegmentation(int[] nodulePixels) {
    	 
    	ArrayList<Channel> channels = new ArrayList<Channel>(); 
     	channels.add(Channel.Red);
     	
    	ColorClustering cluster = new ColorClustering(image);
    	
    	
    	File file = null;
		
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Choose a Red ONLY clusterer model.");
           
        String extension = fileChooser.getSelectedFile().getName().substring(fileChooser.getSelectedFile().getName().lastIndexOf(".") + 1);
        
        if(extension.equalsIgnoreCase("model")) {
        	file = fileChooser.getSelectedFile();
        }
        
       if(file == null) {
    	   IJ.log("Sorry, but the file you selected is not valid.");
    	   return nodulePixels;
       }
       
		cluster.loadClusterer(file.getAbsolutePath());
		cluster.setChannels(channels);
		cluster.setNumSamples(image.getWidth() * image.getHeight());

		FeatureStackArray tempFSA = cluster.createFSArray(image);
		
    	ImagePlus redBinaryMap = cluster.createProbabilityMaps(tempFSA);
		
    	byte[] assignments = getAssignments(redBinaryMap);
    	redBinaryMap.show();
    	
 		List<Integer> redPixelsList = new ArrayList<>();
 		
 		for (int ii = 0; ii < numInstances; ii++) {
 			if (assignments[ii] == 0) { // == 0 ==> segmented nodule pixel.
 				redPixelsList.add(ii);
 			}
 		}
 		
 		int[] nodulePixels2 = new int[nodulePixels.length + redPixelsList.size()];
 		
		System.arraycopy(nodulePixels, 0, nodulePixels2, 0, nodulePixels.length);
 		int counter = 0;
		for( int ii = nodulePixels.length; ii < nodulePixels2.length; ii++) {
			nodulePixels2[ii] = redPixelsList.get(counter++);
		}
		
		
		int[] uniqueArray = Arrays.stream(nodulePixels2).distinct().toArray();
		Arrays.sort(uniqueArray);
		
		updateBinaryMap(uniqueArray);
		
		return uniqueArray;
		
     }
     
     
//   ==================================================
     /**
      * updates the binary map.
      * @param nodulePixels : array of segmented nodule pixels used to override the current binary map.
      */
     private void updateBinaryMap(int[] nodulePixels) {
    	 int width = this.image.getWidth();
    	 int height = this.image.getHeight();
    	 
    	 byte[] pixels = new byte[width*height];
    	 
    	 for( int ii = 0; ii < pixels.length; ii++) {
    		 pixels[ii] = (byte) 255;
    	 }
    	 
    	 
    	 for( int ii = 0; ii < nodulePixels.length; ii++) {
    		 pixels[nodulePixels[ii]] = 0;
    	 }
    	 
    	 ByteProcessor bip = new ByteProcessor(width, height, pixels);
    	 
    	 this.binarymap = new ImagePlus("binary map", bip);
     }

     
//  =======================================
     /**
      * Returns an image with the segmented nodule pixels set to white. 
      * A use case is to see how much of the nodules are not caught in segmentation.
      */
     public ImagePlus imageWithoutNodules() {
    	 int width = this.image.getWidth();
    	 ColorProcessor cip = this.image.getProcessor().convertToColorProcessor();
    	 int x,y;
    	 
    	 for( int loc : red.getPixels()) {
    		  x = loc % width;
    		  y = (int) loc / width;
    		 
    		 cip.putPixel(x, y, BLACK);
    	 }
    	 for( int loc : green.getPixels()) {
    		  x = loc % width;
    		  y = (int) loc / width;
    		 
    		 cip.putPixel(x, y, BLACK);
    	 }
    	 for( int loc : mixed.getPixels()) {
    		  x = loc %  width;
    		  y = (int) loc / width;
    		 
    		 cip.putPixel(x, y, BLACK);
    	 }
    	ImagePlus imageNoNodules = new ImagePlus("Image with nodules removed", cip);
    	imageNoNodules.show();
    	 IJ.saveAs("jpeg","C:\\Users\\Brand\\Documents\\Research\\Nodule_Images\\LabelsTesting\\" + image.getTitle() + "_NoNodules");
    	 return imageNoNodules;
     }
     

     
//   ============================================
     public void run() throws Exception {
    	 
    	 generateSegmentedMap();
    	 
    	 IJ.log("========================");
    	 IJ.log("STARTING RED.GETDATA()  ");
    	 IJ.log("========================");
    	 red.getData(false);
    	 IJ.log("========================");
    	 IJ.log("END RED, STARTING GREEN.");
    	 IJ.log("========================");
    	 green.getData(false);
    	 IJ.log("==========================");
    	 IJ.log("END GREEN, STARTING MIXED.");
    	 IJ.log("==========================");
    	 mixed.getData(true);
    	
    	 
    	 if(mixed.getRois() != null) {
    		 if(mixed.getRois().length > 0) {
    		 IJ.log("==========================");
        	 IJ.log(" FINDING  MIXED  NODULES. ");
        	 IJ.log("==========================");
        	 mixed.findMixedNodules(image, red, green);
    		 }
    	 }
    	// Visualize().show();
    	 
    	 
    	 UserEditsHandler corrections = new UserEditsHandler(this.image, red, green, mixed);
    	 corrections.run();
    	 manager.reset();
    	 manager.close();
    	 WindowManager.closeAllWindows();
    	
    	 return;
    	 
  /*
    	 System.out.println("==========================");
    	 System.out.println("        SAVING DATA       ");
    	 System.out.println("==========================");
    	 saveCombinedLabels();
    	 saveCSV();
    	 
    	 manager.reset();
    	 manager.close();
    	 WindowManager.closeAllWindows();
    	 System.out.println("==========================");
    	 System.out.println("       FINISHED      " + this.image.getShortTitle());
    	 System.out.println("==========================");
    	 */
     }
     
     
     /** calculating distance between two points and updating minimum distance as needed. */
     private void updateDistance(double x1, double y1, double x2, double y2, int[] distance) {
    	 double distanceToCompare;
    	 
    	 distanceToCompare =Math.sqrt( ((y2-y1) * (y2-y1)) + ((x2-x1)*(x2-1)) );
    	 
		   if(distanceToCompare < distance[0]) {
			   distance[0] = (int) distanceToCompare;
		   }
     }
     
     
     
/** Iterate through all boundary points along Rectangle 2 to find smallest points.*/
     private void iteratePath2(int[] distance, Rectangle r2, double[] segment1) {
    	 
    	 
    	 PathIterator path2 = r2.getPathIterator(null);
    	 
    	 double[] segment2 = new double[6];
    	 double x1 = segment1[0];
		 double y1 = segment1[1];
		  
    	 while(!path2.isDone()) {
			   if(path2.currentSegment(segment2) == PathIterator.SEG_LINETO) {
				   double x2 = segment2[0];
				   double y2 = segment2[1];
				   updateDistance(x1,y1,x2,y2,distance);
			   }
			   path2.next();
		   }
     }
     
     
    /**
     * saves original image with ROI's outlined and labeled. Labeling starts
     * with the red nodules, then green, then mixed.
     */
     public void saveCombinedLabels() {
    	 
    	 manager.reset();
    	 manager.setVisible(true);
    	 manager.setEnabled(true);

    	 int ii = 0;
    	 
    	 image.show();
    	 
    	 try {
    	 for (Roi red : red.getRois()) {
    		 manager.add(image, red, ii++);
    	 }
    	 }catch(Exception e) {
    		 IJ.log("No red ROI's found.");
    	 }
    	 
    	 try {
    	 for ( Roi green : green.getRois()) {
    		 manager.add(image,green,ii++); 
    	 }
    	 }catch(Exception e) {
    		 IJ.log("No green ROI's found.");
    	 }
    	 try {
    	 for ( Roi mixedRoi : mixed.getRois()) {
    		 int index = mixed.getIndex(mixedRoi);
    		 if( mixed.getClumped()[index][0] == -1 && mixed.getClumped()[index][1] == -1) {
    			 manager.add(image,mixedRoi,ii++);
    		 }
    	 }
    	 }catch(Exception e) {
    		 IJ.log("No Mixed ROI's found.");
    	 }
    	 
    	 
    	 Overlay overlay = new Overlay();
    	 image.setOverlay(overlay);
    	 
    	 
    	 for (int i = 0; i < manager.getCount(); i++) {
    		    Roi roi = manager.getRoi(i);
    		    
    		    
    		    roi.setPosition(0);
    		    roi.update(true, false);
    		    	
    		    // Add outlines to the ROI
    		    roi.setStrokeColor(Color.white);
    		    roi.setStrokeWidth(2);
    		    
    		    image.getOverlay().add(roi);
    		    
    		}
    	 
		    image.getOverlay().drawNames(false);
		    image.getOverlay().drawBackgrounds(true);
		 
		    
		    try {
		    image.flattenStack();
		    }catch(Exception e) {
		    	IJ.log("No ROI's to overlay.");
		    }
		    
    	
    	 IJ.saveAs("jpeg","D:\\1EDUCATION\\aRESEARCH\\PlantRoots\\NoduleDataV0.9\\OutlinedImages\\" + image.getTitle() + "_Outlined");
    	image.setOverlay(overlay);
    	image.getOverlay().setLabelFontSize(20, "bold");
 	 	image.getOverlay().drawLabels(true);
    	image.getOverlay().setLabelColor(Color.white);
		image.getOverlay().setLabelFont(overlay.getLabelFont());
    	 
    	IJ.saveAs("jpeg","D:\\1EDUCATION\\aRESEARCH\\PlantRoots\\NoduleDataV0.9\\Annotated_Images\\" + image.getTitle() + "_Annotated");
    	
    	image.close();
    	IJ.log("All Roi's added to original image.");
    	
    	
     }
     
     
//   =======================
     /**
      * saves the generated data as a CSV file. 
      */
     public void saveCSV() {
    	 
     	if (green.getMap() == null || red.getMap() == null || mixed.getMap() == null) {
     		System.out.println("generating map");
     		generateSegmentedMap();
     	}
     	
     	if(green.getArea() == null ) {
     		green.getData(true);
     	}
     	if(red.getArea() == null) {
     		red.getData(true);
     	}
     	if(mixed.getArea() == null) {
     		mixed.getData(false);
     	}
     	
     	int[][] redAreas = red.getArea();
     	int[][] greenAreas = green.getArea();
     	int[][] mixedAreas = mixed.getArea();
     	
     	
     	
     	//SaveDialog saver = new SaveDialog("Save CSV File", FilenameUtils.removeExtension(image.getTitle())+ "_data.csv", "");
  		//String dir = saver.getDirectory();
     	//String name = saver.getFileName();
     	
     	String dir = "D:\\1EDUCATION\\aRESEARCH\\PlantRoots\\NoduleDataV0.9\\CSV_Files\\";
  		String name = FilenameUtils.removeExtension(image.getTitle());
  		
  		if ( dir == null || name == null) {
  			IJ.log("saving cancelled");
  			return;
  		}
  		
  		String save = dir + name + ".csv";
  		
  		
  		int numRedNods = red.numNodules;
  		int numGreenNods = green.numNodules;
  		int numMixedNods = mixed.numNodules;
  		
  		try {
  		for( NoduleClump clump : red.getClumps()) {
     		if(clump.hasMixed) {
     			numRedNods--;
     			clump.numNodules--;
     			redAreas[clump.index][0]--;
     		}
  		}
  		}catch(Exception e) {
  			System.out.println("No red nodule Clumps");
  		}
  		
  		
  		try {
  		for( NoduleClump clump : green.getClumps()) {
  			if(clump.hasMixed) {
  				numGreenNods--;
  				clump.numNodules--;
  				greenAreas[clump.index][0]--;
  				
  			}
  		}
  		}catch(Exception e) {
  			System.out.println("No green nodule clumps.");
  		}
  		
  		if( numRedNods == -1) {
  			numRedNods=0;
  		}
  		if(numGreenNods == -1) {
  			numGreenNods=0;
  		}
  		if(numMixedNods == -1) {
  			numMixedNods=0;
  		}
  		
  		int numRedRois = 0;
  		int numGreenRois = 0;
  		
  		
  		if(red.getRois() == null) {
  			numRedRois = 0;
  		}
  		else {
  			numRedRois = red.getRois().length;
  		}
  		if(green.getRois() ==null) {
  			numGreenRois = 0;
  		}
  		else {
  			numGreenRois = green.getRois().length;
  		}
  		
  		
  		
     	String[] header = {"Roi", "Area", "Color", "Red Pixel Count", "Green Pixel Count", "circularity"};
     	String[][] mat = new String[numRedNods + numGreenNods + numMixedNods+1][6];
     	mat[0] = header;
     	
     	
     	
     	int roiCounter = 0;
     	for(int ii = 1; ii < numRedNods+1; ii++) {
     		
     		if(redAreas[0][1] == 0) {
     			break;
     		}
     		if ( redAreas[roiCounter][0] == 1) {
     		mat[ii][0] = Integer.toString(roiCounter+1);
     		mat[ii][1] = Integer.toString(redAreas[roiCounter][1]);
     		mat[ii][2] = "Red";
     		mat[ii][3] = Integer.toString(redAreas[roiCounter][1]);
	     	mat[ii][4] = "0";
	     	mat[ii][5] = Double.toString(red.getCircularity(roiCounter));
     		}
     		else {
     			System.out.println("multiple red nodules in this roi.");
     			int cc = 1;
     			for ( int jj = 0; jj < redAreas[roiCounter][0]; jj++) {
     				mat[ii][0] = Integer.toString(roiCounter+1) + "_" + cc++;
     	     		mat[ii][1] = Integer.toString(redAreas[roiCounter][1]);
     	     		mat[ii][2] = "Red";
     	     		mat[ii][3] = Integer.toString(redAreas[roiCounter][1]);
     	     		mat[ii][4] = "0";
     	     		mat[ii][5] = Double.toString(red.getCircularity(roiCounter));
     	     		ii++;
     			}
     			ii -=1;
     		}
     		
     		roiCounter++;
     	}
     	
     	
     	roiCounter = 0;
     	for(int ii = 0; ii < numGreenNods; ii++) {
     		
     		if(greenAreas[0][1] == 0) {
     			break;
     		}
     		
     		int matIndex = ii + numRedNods+1;
     		
     		if ( greenAreas[roiCounter][0] == 1) {
     		mat[matIndex][0] = Integer.toString(roiCounter+numRedRois+1);
     		mat[matIndex][1] = Integer.toString(greenAreas[roiCounter][1]);
     		mat[matIndex][2] = "Green";
     		mat[matIndex][3] = "0";
	     	mat[matIndex][4] = Integer.toString(greenAreas[roiCounter][1]);
	     	mat[matIndex][5] = Double.toString(green.getCircularity(roiCounter));
     		}
     		
     		else {
     			System.out.println("multiple green nodules in this roi.");
     			int pause = roiCounter;
 				int pause2 = roiCounter + numRedRois+1;
 				int numNodulesInClump = greenAreas[roiCounter][0];
 				int cc = 1;
     			for ( int jj = 0; jj < numNodulesInClump; jj++) {
     				
     				matIndex = ii + numRedNods+1;
     				mat[matIndex][0] = Integer.toString(pause2) + "_" + cc++;
     	     		mat[matIndex][1] = Integer.toString(greenAreas[pause][1]);
     	     		mat[matIndex][2] = "Green";
     	     		mat[matIndex][3] = "0";
     	     		mat[matIndex][4] = Integer.toString(greenAreas[pause][1]);
     	     		mat[matIndex][5] = Double.toString(green.getCircularity(pause));
     	     		ii++;
     			}
     			ii -=1;
     		}
     		roiCounter++;
     		
     		
     	}
     	
     	roiCounter = 0;
     	for(int ii = 0; ii < numMixedNods; ii++) {
     		if(mixedAreas[0][1] == 0) {
     			break;
     		}
     		int matIndex = ii + numRedNods + numGreenNods + 1;
     		int clumpIndex = Integer.max(mixed.getClumped()[roiCounter][0], mixed.getClumped()[roiCounter][1]);
     		if(clumpIndex == -1) {
         	mat[matIndex][0] = Integer.toString(roiCounter+numRedRois + numGreenRois+1);
     		}
     		
     		else {
     			if(mixed.getClumped()[roiCounter][0] > mixed.getClumped()[roiCounter][1]) {
     				mat[matIndex][0] = Integer.toString(clumpIndex+1) + "_" + Integer.toString((red.getClump(clumpIndex).numNodules)+1);
     			}
     			else {
     				mat[matIndex][0] = Integer.toString(numRedRois + clumpIndex+1) + "_" + Integer.toString((green.getClump(clumpIndex).numNodules)+1);
     			}
     		}
     		
         	mat[matIndex][1] = Integer.toString(mixedAreas[roiCounter][1]);
         	mat[matIndex][2] = "Mixed";
         	mat[matIndex][3] = Integer.toString( mixed.getPixelArea(ii, "red") );
         	mat[matIndex][4] = Integer.toString( mixed.getPixelArea(ii, "green") );
         	roiCounter++;
     	}
     	
     	
     	try(FileWriter writer = new FileWriter(save)){
     		StringJoiner comma = new StringJoiner(",");
     		for ( String[] row : mat) {
     			comma.setEmptyValue("");
     			comma.add(String.join(",", row));
     			writer.write(comma.toString());
     			writer.write(System.lineSeparator());
     			comma = new StringJoiner(",");
     		}
     		
     		writer.flush();
     		writer.close();
     	}catch(IOException e) {
     		System.err.println("Error writing CSV file: " + e.getMessage());
     	}
      }
     
     
     /**@deprecated */
     public void simpleSaveCSV() {
    	if (green == null || red == null) {
    		System.out.println("generating map");
    		generateSegmentedMap();
    	}
    	
    	if(green.getArea() == null || red.getArea() == null) {
    		//red.getData(true);
    		//green.getData(true);
    	}
    	
    	int[][] redAreas = red.getArea();
    	int[][] greenAreas = green.getArea();
    	
    	//SaveDialog saver = new SaveDialog("Save CSV File", FilenameUtils.removeExtension(image.getTitle())+ "_data.csv", "");
 		//String dir = saver.getDirectory();
    	//String name = saver.getFileName();
    	
    	String dir = "C:\\Users\\Brand\\Documents\\Research\\Nodule_Images\\LabelsTesting\\";
 		String name = FilenameUtils.removeExtension(image.getTitle());
 		
 		if ( dir == null || name == null) {
 			IJ.log("saving cancelled");
 			return;
 		}
 		
 		String save = dir + name + ".csv";
 		
    	int numRows = redAreas.length + greenAreas.length;
    	
    	String[] header = {"", "numNodules", "Area", "Color"};
    	String[][] mat = new String[numRows][4];
    	mat[0] = header;
    	
    	for(int ii = 1; ii < mat.length; ii++) {
    		
    		if ( ii < redAreas.length) {
    			
    			mat[ii][0] = Integer.toString(ii);
    			mat[ii][1] = Integer.toString(redAreas[ii][0]);
    			mat[ii][2] = Integer.toString(redAreas[ii][1]);
    			mat[ii][3] = "red";
    		}
    		else {
    			
    			try {
    				mat[ii][0] = Integer.toString(ii-redAreas.length+1);
    				mat[ii][1] = Integer.toString(greenAreas[ii-redAreas.length][0]);
    				mat[ii][2] = Integer.toString(greenAreas[ii-redAreas.length][1]);
    				mat[ii][3] = "green";
    			}catch(Exception e) {
    				System.out.println("filling green matrix failed. Ignoring.");
    			}
    		}
    	}
    	
    	try(FileWriter writer = new FileWriter(save)){
    		writer.write(System.lineSeparator());
    		StringJoiner comma = new StringJoiner(",");
    		writer.write(name);
    		writer.write(System.lineSeparator());
    		for ( String[] row : mat) {
    			comma.setEmptyValue("");
    			comma.add(String.join(",", row));
    			writer.write(comma.toString());
    			writer.write(System.lineSeparator());
    			comma = new StringJoiner(",");
    		}
    		
    		writer.write("total red,"+red.numNodules+",0,red");
    		writer.write(System.lineSeparator());
    		writer.write("total green,"+green.numNodules+",0,green");
    		writer.flush();
    		writer.close();
    	}catch(IOException e) {
    		System.err.println("Error writing CSV file: " + e.getMessage());
    	}
     }
     
     
//  ========================================================
    /**
     * Creates binary map.
     * @param pixels : pixels to be drawn as foreground
     * @param color : color to be used.
     * @return : colored binary map(white and one chosen RGB color)
     */
	private ImagePlus createMap(int[] pixels, int[] color) {
		
		int width = binarymap.getWidth();
		ColorProcessor nmp = new ColorProcessor(binarymap.getWidth(), binarymap.getHeight());
		
		
		for( int ii = 0; ii < pixels.length; ii++) {
			int loc = pixels[ii];
			int x = loc % width;
			int y = (loc - x) / width;
			nmp.putPixel(x, y, color);
		}
		
		if( color[0] == 255 && color[1] == 255) {
			return new ImagePlus(image.getTitle() + "_mixed", nmp);
		}
		else if ( color[0] == 255) {
			return new ImagePlus(image.getTitle() + "_red", nmp);
		}
		else {
			return new ImagePlus(image.getTitle() + "_green",nmp);
		}
	}//===========================================================
	
	
/**@return a graph of pixels plotted R against G with segmenting lines.*/
	public ImagePlus Visualize() {
		
		int[] clusterAssignments = getAssignmentsRandom();
		Attribute redatt = instances.attribute(0);
		Attribute greenatt = instances.attribute(1);
		
		double[] xDataCluster0 = new double[numInstances];
		double[] yDataCluster0 = new double[numInstances];
		double[] xDataCluster1 = new double[numInstances];
		double[] yDataCluster1 = new double[numInstances];
		
		
		int cluster0 = 0;
		int cluster1 = 0;
		
		for (int ii=0; ii <numInstances; ii++) {
			double rValue = instances.get(ii).value(redatt);
			double gValue = instances.get(ii).value(greenatt);
			if (clusterAssignments[ii] ==0){
				xDataCluster0[cluster0] = rValue;
				yDataCluster0[cluster0] = gValue;
				cluster0++;
			}
			else if (clusterAssignments[ii] == 1) { 
				xDataCluster1[cluster1] = rValue;
				yDataCluster1[cluster1] = gValue;
				cluster1++;
			}
		}
		
		/*
		Roi tempRoi =  new Roi(3680, 2440, 45, 37);
		double[] xTest = new double[tempRoi.getContainedPoints().length];
		double[] yTest = new double[tempRoi.getContainedPoints().length];
		int ij = 0;
		for(Point p : tempRoi) {
			xTest[ij] = this.image.getPixel(p.x, p.y)[0];
			yTest[ij++] = this.image.getPixel(p.x, p.y)[1];
			plot.addPoints(xTest, yTest, Plot.CIRCLE);
		}
		*/
		
		// Trim the arrays to remove unused elements
		double[] xDataCluster0Trimmed = new double[cluster0];
		double[] yDataCluster0Trimmed = new double[cluster0];
		System.arraycopy(xDataCluster0, 0, xDataCluster0Trimmed, 0, cluster0);
		System.arraycopy(yDataCluster0, 0, yDataCluster0Trimmed, 0, cluster0);

		double[] xDataCluster1Trimmed = new double[cluster1];
		double[] yDataCluster1Trimmed = new double[cluster1];
		System.arraycopy(xDataCluster1, 0, xDataCluster1Trimmed, 0, cluster1);
		System.arraycopy(yDataCluster1, 0, yDataCluster1Trimmed, 0, cluster1);
		
		Plot plot = new Plot("cluster distribution","Red","Green");
		plot.setAxisXLog(false);
		plot.setAxisYLog(false);
		plot.setLimits(0, 255, 0, 255);
		plot.setColor(Color.BLUE);
		plot.addPoints(xDataCluster0Trimmed, yDataCluster0Trimmed, Plot.DOT);
		
		plot.setColor(Color.RED);
		plot.addPoints(xDataCluster1Trimmed, yDataCluster1Trimmed, Plot.DOT);
		plot.setColor(Color.BLACK);
		
		
		plot.setColor(Color.GREEN);
		int[] topLine = Line(TOPCUTOFFSLOPE,TOPB);
		int[] RLine = new int[] {(int) RLINE, 0, (int) RLINE, 255};
		double[][] values = parabola(BOTTOMA,0,BOTTOMB);
		
		for(int ii = 0; ii < values.length-1; ii++) {
			plot.drawLine(values[ii][0], values[ii][1], values[ii+1][0], values[ii+1][1]);
		}
		
		plot.drawLine(topLine[0], topLine[1], topLine[2], topLine[3]);
		plot.drawLine(RLine[0], RLine[1], RLine[2], RLine[3]);
		
		
		
		plot.setColor(Color.BLACK);
		int[] initialSegmentationLine = Line(initialSegmentationSlope, initialSegmentationB);
		plot.drawLine(initialSegmentationLine[0], initialSegmentationLine[1], initialSegmentationLine[2], initialSegmentationLine[3]);
		
		ImageProcessor plotImageProcessor = plot.getProcessor();
		ImagePlus image = new ImagePlus(this.image.getShortTitle() + "_Color_Distribution", plotImageProcessor);
		
		return image;
		
	}
	

/**
 * 
 * @return map containing all nodules highlighted with their respective color.
 */
	public ImagePlus combinedMap() {
		
		int[] greenNods = green.getPixels();
		int[] redNods = red.getPixels();
		int[] mixedNods = mixed.getPixels();
		
		ColorProcessor nmp = new ColorProcessor(this.image.getWidth(), this.image.getHeight());
		
	
		
		for (int kk = 0; kk < redNods.length; kk++) {
			int loc = redNods[kk];
			int x = loc % binarymap.getWidth();
			int y = (loc - x) / binarymap.getWidth();
			nmp.putPixel(x, y, RED);
		}
		
		for (int ii = 0; ii < greenNods.length; ii++) {
			int loc = greenNods[ii];
			int x = loc % binarymap.getWidth();
			int y = (loc - x) / binarymap.getWidth();
			nmp.putPixel(x, y, GREEN);
		}
		
		for (int kk = 0; kk < mixedNods.length; kk++) {
			int loc = mixedNods[kk];
			int x = loc % binarymap.getWidth();
			int y = (loc - x) / binarymap.getWidth();
			nmp.putPixel(x, y, YELLOW);
		}
		ImagePlus combinedMap= new ImagePlus(image.getTitle() + "_combined", nmp.getBufferedImage());
		
		return combinedMap;
	}

	
/**@return returns the roi that is within the other, or null if neither is contained within the other.*/
	private Roi Contains(Roi roi0, Roi roi2) {
		
		Rectangle rect0 = roi0.getBounds();
		Rectangle rect1 = roi0.getBounds();
		
		if(rect0.contains(rect1)) {
			return roi2;
		}
		
		if(rect1.contains(rect0)) {
			return roi0;
		}
		
		return null;
		
	}
	
	
//  ===============================
	/**
	 * saves various test-images to save folder. Not used in pipeline.
	 */
	public void ChannelsTesting() {
			
				String[] channels = {"L"};
				ImagePlus[] im = new ImagePlus[3];
				
				try {
					im = getLargestRoisAsImages("red",3);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				ImageStack[] stack = new ImageStack[3];
				
				stack[0] = getChannelGreyscales(channels,im[0]);
				stack[1] = getChannelGreyscales(channels,im[1]);
				stack[2] = getChannelGreyscales(channels,im[2]);
				ByteProcessor[] bytes = {stack[0].getProcessor(1).convertToByteProcessor(true), stack[1].getProcessor(1).convertToByteProcessor(true),stack[2].getProcessor(1).convertToByteProcessor(true)};
				
				
				ImagePlus ism = new ImagePlus(stack[1].getSliceLabel(2) , stack[1].getProcessor(2));
				FileSaver saver = new FileSaver(ism);
				saver.saveAsJpeg("C:\\Users\\Brand\\Documents\\Research\\Nodule_Images\\DistributionGraphs\\"+ FilenameUtils.removeExtension(image.getShortTitle()) +".jpg");
				
				ImagePlus imm = new ImagePlus(stack[1].getSliceLabel(1) , stack[1].getProcessor(1));
				saver = new FileSaver(imm);
				saver.saveAsJpeg("C:\\Users\\Brand\\Documents\\Research\\Nodule_Images\\DistributionGraphs\\"+ FilenameUtils.removeExtension(image.getShortTitle()) +"_Lightness.jpg");
				intensityDistribution(bytes[0]);
				
				ByteProcessor ip = stack[1].getProcessor(2).convertToByteProcessor(true);
				int mean = meanGreyscaleValue(ip,0);
				int stdev = STdev(ip, 0);
				ColorProcessor test = greyscaleThreshold(ip,(int) (mean-(0.5 *stdev)));
				imm = new ImagePlus(stack[1].getSliceLabel(1),test);
				saver = new FileSaver(imm);
				saver.saveAsJpeg("C:\\Users\\Brand\\Documents\\Research\\Nodule_Images\\DistributionGraphs\\"+ FilenameUtils.removeExtension(image.getShortTitle()) +"_Meanwithback.jpg");
				
				
				mean = meanGreyscaleValue(ip,50);
				stdev = STdev(ip,50);
				test = greyscaleThreshold(ip,(int) (mean-(0.5 * stdev)));
				imm = new ImagePlus(stack[1].getSliceLabel(1),test);
				saver = new FileSaver(imm);
				saver.saveAsJpeg("C:\\Users\\Brand\\Documents\\Research\\Nodule_Images\\DistributionGraphs\\"+ FilenameUtils.removeExtension(image.getShortTitle()) +"_MeanUsinnoback.jpg");
				
		}//=============================================
		
		
/**
 * Not used in pipeline.
 * @param ip
 * @return imagePlus bar chart of the distribution of the image.
 */
//  =============================================
	public ImagePlus intensityDistribution(ByteProcessor ip) {
		Plot plot = new Plot("greyscale distribution", "pixel value", "frequency");
		
		int mean = meanGreyscaleValue(ip, 0);
		int stdev = STdev(ip,0);
		
		float[] arr = flatten(ip.getFloatArray());
		int[] bargraph = new int[256];
		
		for (int ii = 0; ii < arr.length; ii++) {
				try {
					bargraph[ip.get(ii)] ++;
				}catch(Exception e) {
					e.printStackTrace();
				}
		}
		
		plot.setColor(Color.black);
		for (int ii = 0; ii < bargraph.length; ii++) {
			if (ii == mean) {
				plot.setColor(Color.cyan);
			}
			else if(ii == (mean - stdev)) {
				plot.setColor(Color.pink);
			}
			float[] x = new float[bargraph[ii]];
			float[] y = new float[bargraph[ii]];
			for ( int j = 0; j < y.length; j++) {
				x[j] = ii;
				y[j] = j+1;
			}
			plot.addPoints(x, y, 2);
			plot.setColor(Color.black);
		}
		plot.setLimits(-5, 260, 0, 250);
		ImagePlus outImage = new ImagePlus(plot.getTitle(), plot.getProcessor());
		FileSaver saver = new FileSaver(outImage);
		
		
		saver.saveAsJpeg("C:\\Users\\Brand\\Documents\\Research\\Nodule_Images\\DistributionGraphs\\" + FilenameUtils.removeExtension(image.getShortTitle()) +"_distribution.jpg");
		return outImage;
		
	}//===========================================
	

// ===================================================	
/**
 * not used in pipeline
 * @param color : what color ROIs you want.
 * @param numRois :  number of ROIs to make images out of.
 * @return array of images cropped by largest Roi's in this.rois
 */
	public ImagePlus[] getLargestRoisAsImages(String color, int numRois){
		
		if(!color.equalsIgnoreCase("red") && !color.equalsIgnoreCase("green")) {
			IJ.log("error, invalid nodule color option. Please select red or green");
			return null;
		}
		ResultsTable data = new ResultsTable();
		if(color.equalsIgnoreCase("red")) {
			data = red.getTable();
		}
		else if(color.equalsIgnoreCase("green")) {
			data = green.getTable();
		}
		else {
			System.out.println("Error: Incorrect color option, returning null");
			return null;
		}
		
		
			Roi[] bigbois = new Roi[numRois];
			float max = 0;
			int maxindex = -1;
			int[] indices = new int[numRois];
			float[] maxes = new float[numRois];
			float[] areas = data.getColumn(0);
			int jj = 0;
			while(jj < numRois) {
				for(int ii = 0; ii < areas.length; ii ++) {
					boolean present = false;
				
					for (float value : maxes) {
						if (value == areas[ii]) {
							present = true;
						} 
					} 
					
					if (!present) { 
						if (max < areas[ii]) {
							max = areas[ii];
							maxindex = ii;
						} 
					}
				
				}
				indices[jj] = maxindex;
				maxes[jj] = max;
				
				if(color.equalsIgnoreCase("red")) {
					bigbois[jj] = red.getRois()[maxindex];
				}
				else if(color.equalsIgnoreCase("green")) {
					bigbois[jj] = green.getRois()[maxindex];
				}
				max = 0;
				jj++;
			}
			
			return image.crop(bigbois);		
	}//================================
	
	
// ====================================================================
/**
 * not used in pipeline
 * @param channels string of channels to get the greyscale of
 * @param im Image you're breaking down
 * @return array of greyscale images according to channels used
 */
	ImageStack getChannelGreyscales(String[] channels, ImagePlus im) {
		/**
		 * Super weird quirk: If I only use L, a, and Brightness channels, the Brightness channel
		 * image breaks and becomes extremely bright. If I include at least one more channel, it 
		 * doesn't do that. If I remove the R slice after using it, brightness channel remains
		 * unbroken. Not sure why.
		 * 
		 * references:
		 * ColorClustering line 311
		 * ij.process.ImageConverter
		 */
		boolean isgrey = false, isrgb = false, islab = false, ishsb = false;
		ImagePlus grey, rgb, Lab, hsb;
		ImageConverter ic;
		ColorSpaceConverter conv = new ColorSpaceConverter();
		ImageStack stack = new ImageStack();
		stack.addSlice(im.getProcessor());
		
		
		for (String channel : channels) {
			if (channel == "grey") {
				isgrey = true;
			}
			else if(channel == "R" || channel == "G" || channel == "B") {
				isrgb = true;
			}
			else if(channel == "L" || channel == "a" || channel == "b") {
				islab = true;
			}
			else if(channel == "Hue" || channel == "Sat" || channel == "Brightness") {
				ishsb = true;
			}
		} 
		
		
		if (isgrey) {
			 grey = new ImagePlus("grey", im.getProcessor());
			 ic = new ImageConverter(grey);
			 ic.convertToGray8();
		}else {grey=null;}
		
		if (isrgb) {
			 rgb = new ImagePlus("rgb", im.getProcessor());
			 ic = new ImageConverter(rgb);
			 ic.convertToRGBStack();
		}else {rgb=null;}
		
		if (islab) {
			 Lab = new ImagePlus("Lab", im.getProcessor());
			 ic = new ImageConverter(Lab);
			 Lab = conv.RGBToLab(Lab);
		}else {Lab=null;}
		
		if (ishsb) {
			 hsb = new ImagePlus("hsb", im.getProcessor());
			 ic = new ImageConverter(hsb);
			 ic.convertToHSB();
		}else {hsb=null;}
		
	
		for(String choice : channels) {
			switch(choice) {
			case "grey":
				stack.addSlice("grey", grey.getProcessor().convertToFloat());
				break;
			case "R":
				stack.addSlice("R", rgb.getStack().getProcessor(1).convertToFloat());
				break;
			
			case "G":
				stack.addSlice("G", rgb.getStack().getProcessor(2).convertToFloat());
				break;
					
			case "B":
				stack.addSlice("B", rgb.getStack().getProcessor(3).convertToFloat());
				break;
					
			case "L":
				stack.addSlice("L", Lab.getStack().getProcessor(1).convertToFloat());
				break;
					
			case "a":
				stack.addSlice("a", Lab.getStack().getProcessor(2).convertToFloat());
				break;
					
			case "b":
				stack.addSlice("b", Lab.getStack().getProcessor(3).convertToFloat());
				break;
				
			case "Hue":
				stack.addSlice("Hue", hsb.getStack().getProcessor(1).convertToFloat());
				break;
					
			case "Sat":
				stack.addSlice("Sat", hsb.getStack().getProcessor(2).convertToFloat());
				
				break;
					
			case "Brightness":
				stack.addSlice("Brightness", hsb.getStack().getProcessor(3).convertToFloat());
				break;
			}//switch case
				
		} 
			
			
		return stack;
	}//===============================================================

	

//  ===============================================================
	/**
	 * not used in pipeline
	 * @param ip
	 * @param thresh
	 * @return
	 */
	public ColorProcessor greyscaleThreshold(ByteProcessor ip, int thresh) {
		
		int width = ip.getWidth();
		int height = ip.getHeight();
		int size = width * height;
		
		ArrayList<Integer> dark = new ArrayList<>();
		
		for (int ii = 0; ii < size; ii++) {
			if (ip.get(ii) < thresh){
				dark.add(ii);
			}
		}
		//System.out.println(dark.size());
		ColorProcessor output = ip.convertToColorProcessor();
		
		for (int ii = 0; ii < dark.size(); ii++) {
			int x = dark.get(ii) % width;
			int y = (dark.get(ii)-x) / width;
			output.putPixel(x, y, BLUE);
		}
		
		return output;
	}//==============================================================
	
	
/**
 * not used in pipeline.
 * 
 * @param backThresh integer value to denote the greyscale pixel value that is used for finding background
 * @param ip ByteProcessor 
 * @return standard deviation of the dispersion of non-background pixels of the image.
 */
	int STdev(ByteProcessor ip, int backThresh) {
		int mean = meanGreyscaleValue(ip,backThresh);
		double stdev = 0;
		int counter = 0;
		// bargraph[0] == bargraph[50]. 
		int[] bargraph  = new int[255-backThresh];
		for (int ii = 0; ii < bargraph.length; ii++) {
			bargraph[ii] = 0;
		}
		
		int size = ip.getWidth() * ip.getHeight();
		
		for (int ii = 0; ii < (size); ii++) {
			if (ip.get(ii) > backThresh) {
				stdev += ((ip.get(ii) - mean) * (ip.get(ii)-mean));
			}
			else {
				counter++;
			}
		}
		
		stdev =  Math.round((stdev / (size - counter-1)));
		stdev = Math.round(Math.sqrt(stdev));
		return (int) stdev;
	}
	
	
	/**
	 * not used in pipeline.
	 * @param im
	 * @param weights
	 * @return image that is the weighted sum of the pixel values of greyscale images.
	 */
	public FloatProcessor greyscaleWeightedSum(ImageStack im, double[] weights) {
		ImageProcessor ip = im.getProcessor(1);
		im.deleteSlice(1); // removing original slice for indexing reasons. Will add it back aftewards.
		
		
		if (im.size() != weights.length) {
			System.out.println("error: length error");
		}
		
		
		RealMatrix[] im1 = new Array2DRowRealMatrix[im.size()];
		RealMatrix combine = null;
		
		
		for ( int ii = 1; ii < im.size()+1; ii++) {
		
			float[][] temp = im.getProcessor(ii).getFloatArray();
			double[][] temp1 = floatToDouble(temp);
			if ( ii == 1 ) {
				combine = new Array2DRowRealMatrix(temp1);
			}
			
			im1[ii-1] = new Array2DRowRealMatrix(temp1);
		}
	
		combine = combine.scalarMultiply(weights[0]);
		
		for ( int ii = 1; ii < im.size() ; ii++) {
			combine = combine.add(im1[ii].scalarMultiply(weights[ii]));
		}
		combine = combine.scalarMultiply( (1 / weights.length) );
		double[][] comb = combine.getData();
		
		im.addSlice("original", ip, 0);
		return new FloatProcessor(comb.length, comb[0].length, flatten(doubleToFloat(comb)));
	}//===============================================================
	
	
	/**
	 * not used in pipeline.
	 * @param floatArray
	 * @return doubleArray
	 */
	double[][] floatToDouble(float[][] floatArray){

		double[][] doubleArray = new double[floatArray.length][floatArray[0].length];
		
		for (int i = 0; i < floatArray.length; i++) {
            for (int j = 0; j < floatArray[i].length; j++) {
                doubleArray[i][j] = floatArray[i][j]; // Casting from float to double
            }
        }
		
		return doubleArray;
	}//===========================================
	
	
	
	/**
	 * not used in pipeline.
	 * @param ip
	 * @param backThresh threshold for excluding background pixels in mean calculation.
	 * @return mean greyscale value 
	 */
	//  =========================================================
	int meanGreyscaleValue(ByteProcessor ip, int thresh) {
			double mean = 0;
			
			int width = ip.getWidth();
			int height = ip.getHeight();
			int size = width*height;
			int counter = 0;
			
			for (int ii = 0; ii < size; ii++) {
					mean += ip.get(ii);
					counter ++;
			}
			
			mean = mean / (size - counter);
			
			return (int) Math.round(mean);
			
		}//=========================================
	

/**
 * not used in pipeline
* @param floatArray2D
 * @return floatArray1D flattened row-wise
 */
	float[] flatten(float[][] floatArray2D) {
		int size = floatArray2D.length * floatArray2D[0].length;
		float[] floatArray1D = new float[size];
		int ii = 0;
		
		for (float[] row : floatArray2D) {
			for (float element: row) {
				floatArray1D[ii++] = element;
			}
		}
		
		
		return floatArray1D;
	}
	
	
//  =================================================
/**
 * not used in pipeline
 * @param doubleArray
 * @return floatArray
 */
	float[][] doubleToFloat(double[][] doubleArray){
		float[][] floatArray = new float[doubleArray.length][doubleArray[0].length];
		for (int i = 0; i < doubleArray.length; i++) {
            for (int j = 0; j < doubleArray[i].length; j++) {
                floatArray[i][j] = (float) doubleArray[i][j]; // Casting from double to float
            }
        }
		return floatArray;
	}//==================================================
	
	
	int[][] floatToInt(float[][] floatArray){
		
		int[][] intArray = new int[floatArray.length][floatArray[0].length];
		for (int i = 0; i < floatArray.length; i++) {
            for (int j = 0; j < floatArray[i].length; j++) {
                intArray[i][j] = (int) floatArray[i][j]; // Casting from double to float
            }
        }
		return intArray;
	}
	
	
//  =============================================================
/**
 * greyscaled RG. a niche implementation of getChannelGreyscales
 */
	public void greyRG() {
		ImagePlus[] output = new ImagePlus[2];
		
		ColorProcessor process = (ColorProcessor) image.getProcessor();
		byte[] r = process.getChannel(1);
		ByteProcessor rprocess = new ByteProcessor(image.getWidth(), image.getHeight(), r);
		output[0] = new ImagePlus("red", rprocess);
		
		byte[] g = process.getChannel(2);
		ByteProcessor gprocess = new ByteProcessor(image.getWidth(), image.getHeight(), g);
		output[1] = new ImagePlus("green", gprocess);
		
		output[0].show();
		output[1].show();
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
	
	/**
	 * returns double array of points. Graphed in Quadrant I.
	 * 
	 * ax^2 + bx + c
	 * @return
	 */
	private double[][] parabola(double a, double b, double c) {
		double[][] values = new double[510][2];
		
		for(int ii = 0; ii < 510; ii++) {
			double x = ii/2;
			double y = a * x * x  + b * x + c;
			values[ii][0] = x;
			values[ii][1] = y;
		}
		
		
		return values;
	}
	
	
}
