package noduledata.imagej;


import fiji.util.gui.GenericDialogPlus;
import javax.swing.ImageIcon;

import java.awt.Color;
import java.awt.Rectangle;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;



	/**
	 * An object of this class represents a nodule clump and the data that separates the nodule into
	 * separated nodules via various methods. A supporting class of ColorData.java.
	 * @author Brand
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
		
		public NoduleClump(int index, ShapeRoi roi, double area, int numNodules, ImagePlus imp) {
			this.index = index;
			this.roi = roi;
			this.area = area;
			this.numNodules = numNodules;
			
			/*
			if( numNodules > 10 || area < 1000) {
				try {
					correctClump(imp);
				}catch(Exception e) {
					e.printStackTrace();
					System.out.println("break here.");
				}
				
			}*/
			
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
		 * returns the given rectangle scaled for window viewing.
		 */
		private Rectangle scale(Rectangle rect) {
			int width = rect.width;
			int height = rect.height;
			int x = rect.x;
			int y = rect.y;
			x -= (int) (width/2);
			y -= (int) (height/2);
			return new Rectangle(x,y,width*2,height*2);
		}
		
		/**
		 * returns the given image with the roi outlined.
		 */
		private ImagePlus outline(ImagePlus imp1) {
			
			Roi box = new Roi(roi.getBounds());
			ImagePlus imp = new ImagePlus(imp1.getShortTitle(), imp1.getProcessor());
			
			box.setPosition(0);
 		    box.update(true, false);
 		    box.setStrokeColor(Color.WHITE);
 		    box.setStrokeWidth(3);
 		    
 		    Overlay overlay = new Overlay(box);
 		   
			overlay.drawNames(false);
			overlay.drawBackgrounds(true);
			
			imp.setOverlay(overlay);
			
			imp.flattenStack();
			
			imp.setRoi(scale(this.roi.getBounds()));
			ImagePlus roiImage = imp.crop();
			
			return roiImage;
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
