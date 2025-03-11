package noduledata.imagej;


import fiji.util.gui.GenericDialogPlus;
import javax.swing.ImageIcon;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ShapeRoi;



	/**
	 * An object of this class represents a nodule clump and the data that separates the nodule into
	 * separated nodules via various methods. A supporting class of ColorData.java.
	 * 
	 * 
	 * @author Brandin Farris
	 *
	 */

public class NoduleClump {

		public int index;
		public ShapeRoi roi;
		public double area;
		public int numNodules;
		public boolean hasMixed = false;
		
	
		public NoduleClump() {
			this.index = -1;
			this.roi = null;
			this.area = -1;
			this.numNodules = -1;
		}
		
		
		/**
		 * constructor
		 * 
		 * @param index : index in ColorData.rois of the clump
		 * @param roi : roi object of the clump
		 * @param area : number of pixels in the clump
		 * @param numNodules : number of nodules in the clump
		 * @param imp : image we're doing analysis on
		 */
		public NoduleClump(int index, ShapeRoi roi, double area, int numNodules, ImagePlus imp) {
			this.index = index;
			this.roi = roi;
			this.area = area;
			this.numNodules = numNodules;
		
			
		}
		
		/**
		 * Prompts the user to correct the number of nodules within the clump.
		 * @param image : image with roi of interest outlined.
		 * @return : the number of nodules thought to be in the outlined Roi.
		 */
		private int userInput(ImagePlus image) {
			boolean retry = true;
			boolean scale = false;
			ImagePlus scaledImage = new ImagePlus();
			
			while( retry) {
				
				int maxWidth = 800; // Maximum width for the displayed image
		        int maxHeight = 600; // Maximum height for the displayed image
		        int imageWidth = image.getWidth();
		        int imageHeight = image.getHeight();

		        if (imageWidth > maxWidth || imageHeight > maxHeight) {
		            // Scale down the image while preserving the aspect ratio
		            double widthRatio = (double) maxWidth / imageWidth;
		            double heightRatio = (double) maxHeight / imageHeight;
		            double scaleRatio = Math.min(widthRatio, heightRatio);
		            int scaledWidth = (int) (imageWidth * scaleRatio);
		            int scaledHeight = (int) (imageHeight * scaleRatio);
		            scaledImage = image.resize(scaledWidth, scaledHeight, "none");
		            scale = true;
		        } 
		        
			
				GenericDialogPlus gd = new GenericDialogPlus("Correcting Nodule Count");
				
				if(scale) {
					gd.addImage(new ImageIcon(scaledImage.getBufferedImage()));
				} else {
					gd.addImage(new ImageIcon(image.getBufferedImage()));
				}
				gd.addNumericField("Correct number of nodules: ", 0);
				gd.showDialog();
				
				int numNodules = (int) gd.getNextNumber();
				
				if(numNodules > 0) {
					return numNodules;
				}
				else {
					IJ.log("Error: please enter a positive number.");
					retry = true;
				}
			
			}
			
			return -2;
		}
		
		
		/**
		 * Prompts the user to correct (or not) the number of nodules within the clump.
		 * @param imp : image of roi.
		 * @throws IllegalStateException : if the input received from the user is invalid. 
		 */
		public int correctClump(ImagePlus imp) throws IllegalStateException {
			
			int newNumNodules = userInput(imp);
			
			if(newNumNodules == 0) {
				this.numNodules = 0;
				this.area = 0;
				return -1;
			}
			
			if(newNumNodules == -2) {
				throw new IllegalStateException("Error correcting nodule count.");
			}
			else if(newNumNodules == -1) {
				IJ.log("No changes are made to this ROI. Moving on.");
				return -1;
			}
			else if(newNumNodules > 0) {
				double totalArea = this.numNodules * this.area;
				this.numNodules = newNumNodules;
				this.area = (totalArea / this.numNodules);
				
				IJ.log("Nodule count and area corrected.");
			}
			
			else {
				throw new IllegalStateException("Error correcting nodule count; incorrect newNumNodules.");
			}
			
			return newNumNodules;
		}
}
