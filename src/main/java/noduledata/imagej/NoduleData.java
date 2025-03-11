package noduledata.imagej;


import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import javax.swing.JFileChooser;

import java.io.File;



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

import ij.WindowManager;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.frame.RoiManager;		

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
	
	
	public ImagePlus binarymap = null; // has floatProcessor
	public ImagePlus image;            
	public int numInstances = -1;
	

/** generate a segmentation map using using a ColorClustering model. Initiates 
 * the ColorData objects 
 * @param cluster : cluster object with a loaded image and .model file. 
 * @param redSingle : the upper bound on how large a red nodule can be before assuming it's a clump
 * @param greenSingle : same as above but for green nodules.
 * @param mixedSingle : same as above but for mixed nodules.
 */
//======================================================
	public NoduleData(ColorClustering cluster,int redSingle, int greenSingle, int mixedSingle) throws IllegalStateException {
		
	
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
		
		if(greenSingle == 0 || redSingle == 0 || mixedSingle == 0) {
			throw new IllegalStateException("Cannot have 0 as the upper bound for nodule areas.");
		}
		
		this.green = new ColorData(manager,GREEN, greenSingle, this.image);
		
		this.red = new ColorData(manager, RED, redSingle, this.image);
		
		this.mixed = new MixedData(manager,YELLOW, mixedSingle, this.image);
		
		
		cluster.createFeatures();	// intensive
		
		this.instances = CCcluster.getFeaturesInstances();
		
		
		this.numInstances = instances.numInstances();

		System.out.println("=============================");
		System.out.println(this.image.getTitle());
		System.out.println("=============================");
	}
	
	
//  =======================================================	
	/**
	 * Get's the cluster assignments for all pixels in the given image from the buffered image.
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
 					greenThreshold = 70;
 					break;
 					
 				case LIGHT_SECTION_TWO:
 					greenThreshold = 80;
 					break;
 					
 				case LIGHT_SECTION_THREE:
 					greenThreshold = 90;
 					break;
 					
 				case LIGHT_SECTION_FOUR:
 					greenThreshold = 100;
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
 		
 		this.binarymap = newBinaryMap;	
 	
     }//==========================================================
     
     
     
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
    	 return imageNoNodules;
     }
     

     
//   ============================================
     /**
      * This is what is called to compute the data for the given image.
      * 
      * @param saveFile : where to store output after execution.
      */
     public void run(String saveFile) {
    	 
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
    	 
    	 IJ.save(Visualize(), saveFile + "\\vis.png");	
    	 
    	 
    	 UserEditsHandler corrections = new UserEditsHandler(this.image, red, green, mixed);
    	 corrections.run(saveFile);
    	 
    	 manager.reset();
    	 manager.close();
    	 WindowManager.closeAllWindows();
    	 
    	 this.red = null;
    	 this.green = null;
    	 this.mixed = null;
    	 corrections = null;
    	 System.gc();
    	 
    	 return;
    	 
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
		
		plot.setColor(Color.BLACK);
		plot.addPoints(xDataCluster1Trimmed, yDataCluster1Trimmed, Plot.DOT);
		
		
		
		plot.setColor(Color.GREEN);
		int[] topLine = Line(TOPCUTOFFSLOPE,TOPB);
		int[] RLine = new int[] {(int) RLINE, 0, (int) RLINE, 255};
		double[][] values = parabola(BOTTOMA,0,BOTTOMB);
		
		for(int ii = 0; ii < values.length-1; ii++) {
			plot.drawLine(values[ii][0], values[ii][1], values[ii+1][0], values[ii+1][1]);
		}
		
		plot.drawLine(topLine[0], topLine[1], topLine[2], topLine[3]);
		plot.drawLine(RLine[0], RLine[1], RLine[2], RLine[3]);
		
		
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
			


// ====================================================================
/**
 * 
 * @param channels string of channels to get the greyscale of
 * @param im Image you're breaking down
 * @return array of greyscale images according to channels used
 */
	ImageStack getChannelGreyscales(String[] channels, ImagePlus im) {
		
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

}
