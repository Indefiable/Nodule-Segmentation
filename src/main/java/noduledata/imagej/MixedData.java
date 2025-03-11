package noduledata.imagej;

import java.awt.Color;
import java.awt.Point;
import java.awt.Polygon;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import javax.swing.ImageIcon;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Overlay;

import ij.gui.ShapeRoi;
import ij.plugin.frame.RoiManager;




/**
 * 
 * This class handles mixed nodules specifically, as they're approached differently than the other nodules. 
 * I identify mixed nodules by looking for yellow pixels in the segmented binary map. This works because the images we took 
 * of the root system were more zoomed out, and may not work for more high definition images, as mixed nodules themselves are not always
 * yellow, and are often green and red next to each other. 
 * 
 * @author Indefiables
 *
 */
public class MixedData extends ColorData {

	private int BUTTON = -1;
	private int YES = 1;
	private int NO = 0;
	private int[] redPixels;
	private int[] greenPixels;
	// clumped[][] is nx2 where n is the number of mixed nodule rois. for each mixed nodule, 
	// the first element is -1 by default, or the index of the red nodule clump. The second element is the same for green.
	private int[][] clumped;
	private ArrayList<Integer> redClumpIndices = new ArrayList<>();
	private ArrayList<Integer> greenClumpIndices = new ArrayList<>();

	/**
	 * Uses the ColorData constructor method.
	 * 
	 * @param manager : RoiManager object.
	 * @param color : rgb of the nodule type.
	 * @param colorThreshold : pixel threshold for a singular noduel area
	 * @param im : image we're doing analysis on.
	 */
	public MixedData(RoiManager manager, int[] color, int colorThreshold, ImagePlus im) {
		super(manager, color, colorThreshold, im);
	}
	
	/**
	 * returns the number of pixels of the chosen color for the nodule at the given index.
	 */
	public int getPixelArea(int index, String color) {
		if(color == "red") {
			return redPixels[index];
		}
		else if(color == "green") {
			return greenPixels[index];
		}
		else {
			System.out.println("Error: must choose red or green.");
			return -1;
		}
	}
	
	/** 
	 * public method for setting roi at given index.
	 */
	@Override
	public void setRoi(int index, ShapeRoi roi) {
	super.setRoi(index, roi);
	
	if(this.redPixels != null) {
		int[] areas = getColoredArea(roi);
		this.redPixels[index] = areas[0];
		this.greenPixels[index] = areas[1];
	}
	
	}
	 
/** 
 * public method for appending new roi to end of the array.	
 */
	@Override
	public void addRoi(ShapeRoi roi) {
		super.addRoi(roi);
		
		int[][] newClumped = new int[clumped.length+1][2];
		
		for(int ii = 0; ii < newClumped.length-1; ii++) {
			newClumped[ii][0] = clumped[ii][0];
			newClumped[ii][1] = clumped[ii][1];
		}
		
		newClumped[newClumped.length-1][0] = -1;
		newClumped[newClumped.length-1][1] = -1;
		this.clumped = newClumped;
		
		int[] newRedPixels = new int[redPixels.length+1];
		int[] newGreenPixels = new int[greenPixels.length+1];
		
		for(int ii = 0; ii < newRedPixels.length -1; ii++) {
			newRedPixels[ii] = redPixels[ii];
			newGreenPixels[ii] = greenPixels[ii];
		}
		
		int[] color = getColoredArea(roi);
		
		newRedPixels[newRedPixels.length - 1] = color[0];
		newGreenPixels[newGreenPixels.length-1] = color[1];
		
		this.redPixels = newRedPixels;
		this.greenPixels = newGreenPixels;
	}
	
/** 
 * Attempts to find the given roi in the array of roi's, and removes it if it's present.
 * Returns true if the removal was succesful, false if the roi wasn't found.	
 */
	@Override
	public boolean removeRoi(ShapeRoi roi) {
		int index = this.getIndex(roi);
		super.removeRoi(roi);
		
		if(this.greenPixels != null) {
			
		int[] newGreenPixels = new int[this.greenPixels.length - 1];
		int[] newRedPixels = new int[this.redPixels.length - 1];
		int[][] newClumped = new int[this.clumped.length-1][2];
		
		System.arraycopy(this.redPixels, 0, newRedPixels, 0, index);
		System.arraycopy(this.redPixels, index+1, newRedPixels, index, this.redPixels.length - index - 1);
		this.redPixels = newRedPixels;
		
		System.arraycopy(this.greenPixels, 0, newGreenPixels, 0, index);
		System.arraycopy(this.greenPixels, index+1, newGreenPixels, index, this.greenPixels.length - index - 1);
		this.greenPixels = newGreenPixels;
		
		System.arraycopy(this.clumped, 0, newClumped, 0, index);
		System.arraycopy(this.clumped, index+1, newClumped, index, this.clumped.length -index - 1);
		this.clumped = newClumped;
		
		this.numNodules--;
		}
		
		return true;
	}
	
	/**
	 * Asks the user if each Mixed ROI is a true mixed ROI, or just between 
	 * a red and green roi.
	 */
	private void removeFakeNodules() {
		
		ImagePlus image = null;
		
		ArrayList<ShapeRoi> roisToDelete = new ArrayList<ShapeRoi>();
		
		
		for(ShapeRoi roi : this.getRois()) {
			image = UserEditsHandler.getImageFromRoi(roi, NoduleSegmentation.image);
			
			int maxWidth = 800; // Maximum width for the displayed image
	        int maxHeight = 600; // Maximum height for the displayed image
	        int imageWidth = image.getWidth();
	        int imageHeight = image.getHeight();

	        if (imageWidth > maxWidth || imageHeight > maxHeight) {
	            // Scale down the image while preserving the aspect ratio
	            double widthRatio = (double) maxWidth / imageWidth;
	            double heightRatio = (double) maxHeight / imageHeight;
	            double scaleRatio = Math.min(widthRatio, heightRatio);
	             imageWidth = (int) (imageWidth * scaleRatio);
	             imageHeight = (int) (imageHeight * scaleRatio);
	            image = image.resize(imageWidth, imageHeight, "none");
	        } 
	        
		
			GenericDialogPlus gd = new GenericDialogPlus("Is this a mixed nodule?");
			
			gd.addImage(new ImageIcon(image.getBufferedImage()));
			
			gd.addButton("Yes",new ActionListener() {
		        @Override
		        public void actionPerformed(ActionEvent e) {
		        	BUTTON=YES;
		        	gd.dispose();
		        }});
			
			
			gd.addButton("No",new ActionListener() {
		        @Override
		        public void actionPerformed(ActionEvent e) {
		        	BUTTON=NO;
		        	gd.dispose();
		        }});
		        
			gd.setBounds(550, 200,imageWidth+100,imageHeight+100);
	    	gd.toFront();
	    	
	    	
			gd.setVisible(true);
			
			if(BUTTON == NO) {
				roisToDelete.add(roi);
			}
		}
		
		for(int ii = roisToDelete.size()-1; ii > -1 ; ii--) {
			this.removeRoi(roisToDelete.get(ii));
		}
		this.numNodules -= roisToDelete.size();
		
	}
	
	
	/**
	 * Identifies mixed nodules by checking for yellow pixels or by calculating the distance between red and green rois.
	 */
	 public void findMixedNodules(ImagePlus im, ColorData red, ColorData green) {
	    	 
	    	 WindowManager.closeAllWindows();
	    	 
	    	 for(int ii = 0; ii < this.numNodules; ii++) {
	    		 if(this.numNodules == 1) {
	    			 break;
	    		 }
	    		 unionMixedNodules();
	    	 }
	    	 
	    	 removeFakeNodules();
	    	 
	    	 
	    	 setClumped(new int[this.getRois().length][2]);
	    	 for(int ii = 0; ii < this.getClumped().length; ii++) {
	    		 this.getClumped()[ii][0] = -1;
	    		 this.getClumped()[ii][1] = -1;
	    	 }
	    	 
	    	 int counter = 0;
	    	 for(ShapeRoi roi : this.getRois()) {
	    		 if(roi == null) {
	    			 System.out.println("Error: roi null");
	    			 continue;
	    		 }
    		 	int index = this.getIndex(roi);
    		 	ShapeRoi temp = unionNodules( roi,red,green,counter);
    		 	counter++;
    		 	
    		 	if ( temp == null) {
    		 		System.out.println("unionMixedNodule produced null Roi");
    		 		continue;
    		 	}
    		 	
    		 
    		 	this.setRoi(index, temp);
    		 
    		 	
	    		 
	    	 }
	    	 
	    	 this.updateMap();
	    	 updateData(im, red, green);
	    	 
	     }//==================================

	 /**
	  * shows the given roi as a white blob overlaying the original image.
	  * @param roi
	  */
	 public void drawRoi(ShapeRoi roi) {
		 Overlay overlay = new Overlay();
		 
		 ImagePlus imp = new ImagePlus("highlighted Roi", NoduleSegmentation.image.getProcessor());
		 
		    roi.setPosition(0);
		    roi.update(true, false);
		    	
		    // Add outlines to the ROI
		    roi.setStrokeColor(Color.white);
		    roi.setStrokeWidth(2);
		    
		    overlay.add(roi);
		    

		    overlay.drawNames(false);
		    overlay.drawBackgrounds(true);
		 
		    imp.setOverlay(overlay);
		    try {
		    imp.flattenStack();
		    }catch(Exception e) {
		    	System.out.println("No ROI's to overlay.");
		    }
		    
		    imp.show();
		    IJ.log("Breakpoint");
		 
	 }
	 
	 
	 
//   ==================================
	 /**
	  * Merges two mixed nodules into one mixed nodule if they're within 5 pixels of each other.
	  */
	 private void unionMixedNodules() {
		
    	 ShapeRoi testRoi;
    	 
    	 for(int indexToReplace = 0; indexToReplace < this.getRois().length ; indexToReplace++) {
    		 ShapeRoi roi1 = this.getRoi(indexToReplace);
    		 for(ShapeRoi roi2 : this.getRois()) {
    			 Polygon poly1 = roi1.getPolygon();
    			 Polygon poly2 = roi2.getPolygon();
    			 
    			 if(! roi1.equals(roi2)) {
    				 double distance = calculateClosestDistance(poly1, poly2);
    				 
    				 if(distance < 3) {
    					ShapeRoi shapeRoi1 = new ShapeRoi(roi1);
    					testRoi =new ShapeRoi( shapeRoi1.or(roi2).shapeToRoi() );
    					this.setRoi(indexToReplace, testRoi);
    					int index = this.getIndex(roi2);
    					if( !this.removeRoi(roi2)) {
    						System.out.println("Failed to remove Roi.");
    					}
    					else {
    						removeFromClumped(index);
    					}
    					this.numNodules--;
    					indexToReplace = this.getRois().length + 1;
    					break;
    				 }
    			 }
    		 }
    	 }
    	 
    	 return;
	 }
	 
	 /**
	  * removes nodule clump at given idnex.
	  */
	 /** removes given index from the clumped[][] object. */
	    private void removeFromClumped(int index) {
	    	if(this.clumped == null) {
	    		IJ.log("Clumped is already null.");
	    		return;
	    	}
	    	int[][] array = this.clumped;
	        if (index < 0 || index >= array.length) {
	            // Index out of bounds, return the original array
	           return;
	        }

	        // Create a new array with a length one less than the original array
	        int[][] newArray = new int[array.length - 1][2];

	        // Copy the elements from the original array to the new array
	        int newArrayIndex = 0;
	        for (int i = 0; i < array.length; i++) {
	            if (i != index) {
	                newArray[newArrayIndex][0] = array[i][0];
	                newArray[newArrayIndex][1] = array[i][1];
	                newArrayIndex++;
	            }
	        }
	        
	       this.setClumped(newArray);
	    }
	 
	 /**
	  * Merges a mixed nodule roi with a green or red nodule roi if the distance between them is < 1 pixel.
	  * @param mixedNodule : mixed roi to union with red or green nodule rois
	  * @param red : ColorData object carrying all red nodule data 
	  * @param green : ColorData object carrying all green nodule data
	  * @param counter : the index of the mixedNodule
	  * @return : a merged mixed nodule roi
	  */
	 private ShapeRoi unionNodules(ShapeRoi mixedNodule,ColorData red, ColorData green,int counter) {
		 	
		 ShapeRoi returnRoi = mixedNodule;
		 	
			 
			 if(mixedNodule == null) {
				 System.out.println("============");
				 System.out.println("mixedRect is null for the given mixed ROI");
				 return null;
			 }
			 
			 if(green.getRois() != null) {
				 ShapeRoi[] consideredRois = green.getRois();
				 returnRoi = unionGreenNodule(red, green, counter, returnRoi, consideredRois);
			 }
			 
			 if( red.getRois() != null) {
				 ShapeRoi[] consideredRois = red.getRois();
				 returnRoi = unionRedNodules(red, green, counter, returnRoi, consideredRois);
			 }
			 
			
			 
			 return returnRoi;
	     }


	 /**
	  * Uses recursion to iterate through the green nodules, merging them with the mixed nodule if the rois are within 1 pixel.
	  * @param red : ColorData holding red nodule data
	  * @param green : ColorData holding green nodule data
	  * @param counter : index of current mixed nodule roi
	  * @param mixedNodule : mixed nodule roi
	  * @param consideredRois : array of green rois still being checked for merging
	  * @return : mixed nodule roi merged with any green nodule roi within 1 pixel
	  */
	private ShapeRoi unionGreenNodule(ColorData red, ColorData green, int counter, ShapeRoi mixedNodule, ShapeRoi[] consideredRois) {
			Polygon mixedRect = mixedNodule.getPolygon();
			ShapeRoi returnRoi = mixedNodule;
			ShapeRoi testShapeRoi = new ShapeRoi(mixedNodule);
		
			double tempDistance;
			ShapeRoi testRoi;
			
			for( ShapeRoi greenRoi : consideredRois) {
			int currentRoiIndex = green.getIndex(greenRoi);
			
			Polygon greenRect = greenRoi.getPolygon();
		 	if ( greenRect == null) {
			 	System.out.println("=========");
			 	System.out.println(" Green Rect object is null.");
			 	tempDistance = 51;
		 	}
		 	else {
			 	tempDistance = calculateClosestDistance(mixedRect, greenRect);
		 	}
		 	if(tempDistance < 0.5) {
		 		consideredRois = Arrays.stream(consideredRois).filter(e -> !e.equals(greenRoi)).collect(Collectors.toList()).toArray(new ShapeRoi[0]);
		 		
			 	boolean remove = true;
			 	boolean skip = false;
			 	for(NoduleClump clump : green.getClumps()) {
				 	if (clump.index == currentRoiIndex && clump.hasMixed) {
					 	skip = true;
					 	remove = false;
				 	}
				 	else if (clump.index == currentRoiIndex) {
					 	greenClumpIndices.add(clump.index);
					 	this.getClumped()[counter][1] = clump.index;
							clump.hasMixed = true;
							remove = false;
							break;
						}
					}
			 
			 	if (remove) {
				 	green.removeRoi(greenRoi);
				 	
				 	for(int ii = 0; ii < this.getClumped().length; ii++) {
				 		if(this.clumped[ii][1] > currentRoiIndex) {
				 			this.getClumped()[ii][1] -=1;
				 		}
				 	}
				 	
				 
			 	}
			 	if(!skip) {
			 		testRoi =new ShapeRoi( testShapeRoi.or(new ShapeRoi(greenRoi)).shapeToRoi() );
				 	returnRoi = testRoi;
			 		
			 		return unionGreenNodule(red,green,counter, returnRoi, consideredRois);
			 	}
		 	}
		}
		return returnRoi;
	}

	/**
	  * Uses recursion to iterate through the red nodules, merging them with the mixed nodule if the rois are within 1 pixel. 
	  * Almost identical to unionGreenNodule.
	  * @param red : ColorData holding red nodule data
	  * @param green : ColorData holding green nodule data
	  * @param counter : index of current mixed nodule roi
	  * @param mixedNodule : mixed nodule roi
	  * @param consideredRois : array of green rois still being checked for merging
	  * @return : mixed nodule roi merged with any green nodule roi within 1 pixel
	  */
	private ShapeRoi unionRedNodules( ColorData red, ColorData green, int counter, ShapeRoi mixedNodule, ShapeRoi[] consideredRois) {
		Polygon mixedRect = mixedNodule.getPolygon();
		ShapeRoi returnRoi = mixedNodule;
		ShapeRoi testShapeRoi = new ShapeRoi(mixedNodule);
		 
		double tempDistance;
		ShapeRoi testRoi;
		for( ShapeRoi redRoi : consideredRois) {
			int currentRoiIndex = red.getIndex(redRoi);
			Polygon redRect = redRoi.getPolygon();
		 	if( redRect == null) {
			 	System.out.println("===========");
			 	System.out.println("Rect object from Red Nodule ROI is null");
			 	tempDistance = 51;
		 	}
		 
		 	else {
			 	tempDistance = calculateClosestDistance(mixedRect,redRect);
		 	}
		 	
		 	if(tempDistance < 0.5) {
		 		
				boolean remove = true;
				boolean skip = false;
				consideredRois = Arrays.stream(consideredRois).filter(e -> !e.equals(redRoi)).collect(Collectors.toList()).toArray(new ShapeRoi[0]);
				
				for(NoduleClump clump : red.getClumps()) {
					
					if(clump.index == currentRoiIndex && clump.hasMixed) {
						skip = true;
						remove = false;
						break;
					}
					else if (clump.index == currentRoiIndex) {
						
						getClumped()[counter][0] = clump.index;
						redClumpIndices.add(clump.index);
						remove = false;
						clump.hasMixed = true;
						
						break;
					}
				}
			
				if(remove) {
					red.removeRoi(redRoi);
					
					for(int ii = 0; ii < this.getClumped().length; ii++) {
				 		if(this.getClumped()[ii][0] > currentRoiIndex) {
				 			this.getClumped()[ii][0] -=1;
				 		}
				 	}
				}
				
				if (!skip) {
					testRoi = new ShapeRoi( testShapeRoi.or(new ShapeRoi(redRoi)).shapeToRoi() );
					returnRoi = testRoi;
						
					}
					return unionRedNodules(red,green,counter, returnRoi, consideredRois);
				}
			
			}
		
		return returnRoi;
	}
	  
	/** returns the counts of red and green pixels of the mixed nodule roi at the given index.*/
	public String roiPixelCount(int index) {
		return ("red: " +redPixels[index] + ", green: " + greenPixels[index]);
	}

	 //===================================================================================================
	/**
	 * returns the distance between the two line segments.
	 */
	private double lineSegmentDistance(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4) {
	            double uA = ((x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3)) / ((y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1));
	            double uB = ((x2 - x1) * (y1 - y3) - (y2 - y1) * (x1 - x3)) / ((y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1));

	            if (uA >= 0 && uA <= 1 && uB >= 0 && uB <= 1) {
	                double intersectionX = x1 + uA * (x2 - x1);
	                double intersectionY = y1 + uA * (y2 - y1);
	                return Math.sqrt(Math.pow(intersectionX - x1, 2) + Math.pow(intersectionY - y1, 2));
	            }
	          
	            double dist1 = Math.sqrt(Math.pow(x1 - x3, 2) + Math.pow(y1 - y3, 2));
	            double dist2 = Math.sqrt(Math.pow(x1 - x4, 2) + Math.pow(y1 - y4, 2));
	            double dist3 = Math.sqrt(Math.pow(x2 - x3, 2) + Math.pow(y2 - y3, 2));
	            double dist4 = Math.sqrt(Math.pow(x2 - x4, 2) + Math.pow(y2 - y4, 2));

	            return Math.min(Math.min(dist1, dist2), Math.min(dist3, dist4));
	        }
	        
	/**
     * Returns the distance between the two given polygons.
     */
	private double calculateClosestDistance(Polygon polygon1, Polygon polygon2) {
	            double minDistance = Double.POSITIVE_INFINITY;
	           
	            Area area1 = new Area(polygon1);
	            Area area2 = new Area(polygon2);
	            area1.intersect(area2);
	            if(!area1.isEmpty()) {
	            	return 0;
	            }
	            
	         
	            
	            int[] xPoints1 = polygon1.xpoints;
	            int[] yPoints1 = polygon1.ypoints;
	            int[] xPoints2 = polygon2.xpoints;
	            int[] yPoints2 = polygon2.ypoints;

	            // Calculate the distance between all pairs of line segments
	            for (int i = 0; i < xPoints1.length - 1; i+=1) {
	                for (int j = 0; j < xPoints2.length - 1; j+=1) {
	                    double dist = lineSegmentDistance(
	                            xPoints1[i], yPoints1[i], xPoints1[i + 1], yPoints1[i + 1],
	                            xPoints2[j], yPoints2[j], xPoints2[j + 1], yPoints2[j + 1]);

	                    minDistance = Math.min(minDistance, dist);
	                }
	            }
	            
	            return minDistance;
	        }
	//========================================================================================
	        
	/**
	 * Gets the number of red and green pixels of the mixed nodule, given the roi.
	 * @param index
	 * @return int[red area][green area]
	 */
	private int[] getColoredArea(ShapeRoi roi) {
		int[] colorArea = new int[2];
		int greenArea = 0;
		int redArea = 0;
		ImagePlus im = NoduleSegmentation.image;
		
		
		for(Point p : roi) {
			
			int redValue = im.getPixel(p.x, p.y)[0];
			int greenValue = im.getPixel(p.x, p.y)[1];
			
			if(greenValue > redValue *redValue * NoduleData.BOTTOMA + NoduleData.BOTTOMB) {
				greenArea ++;
			}
			
			else {
				redArea++;
			}
		}
		colorArea[0] = redArea;
		colorArea[1] = greenArea;
		
		return colorArea;
	}
	
//  =============================================================
	/**
	 * updates the areas and populates greenPixels and redPixels. 
	 * @param im : original image
	 * @param red : ColorData for red nodules
	 * @param green : ColorData for green nodules.
	 */
	private void updateData(ImagePlus im, ColorData red, ColorData green) {
	    	if(this.getRois() == null) {
	    		IJ.log("No mixed Rois.");
	    		return;
	    	}
	    	if(this.getRois().length == 0 ) {
	    		IJ.log("No mixed Rois.");
	    		return;
	    	}
		int numNods = this.numNodules;
		redPixels = new int[numNods];
		greenPixels = new int[numNods];
		
		boolean isClump = false;
		int newarea = 0;
		
		for( int ii = 0; ii < numNods; ii++) {
			if(clumped[ii][0] != -1 || clumped[ii][1] != -1) {
				isClump = true;
			}
			
			int[] colorArea = getColoredArea(this.getRoi(ii));
			int redArea = colorArea[0];
			int greenArea = colorArea[1];
			newarea = redArea+greenArea;
			
			if(!isClump) {
				this.updateArea(ii, newarea);
			}
			if(clumped[ii][0] == -1) {
				redPixels[ii] = redArea;
			}
			if(clumped[ii][1] == -1) {
				greenPixels[ii] = greenArea;
			}
			
			if(isClump) {
				
				int greenIndex = clumped[ii][1];
				int redIndex = clumped[ii][0];
				
				if(greenIndex != -1) {
					try {
						greenPixels[ii] = (int) green.getClump(greenIndex).area;
						green.getClump(greenIndex).roi = this.getRoi(ii);
						green.setRoi(green.getClump(greenIndex).index, this.getRoi(ii));
					}catch(Exception e) {
						System.out.println("Error: Expected to find green clump attached to mixed nodule.");
						e.printStackTrace();
					}
				}
				if(redIndex != -1) {
					try {
						redPixels[ii] = (int) red.getClump(redIndex).area;
						red.getClump(redIndex).roi = this.getRoi(ii);
						red.setRoi(red.getClump(redIndex).index, this.getRoi(ii));
					}catch(Exception e) {
						System.out.println("Error: Expected to find red clump attached to mixed nodule.");
						e.printStackTrace();
					}
				}
				this.updateArea(ii, (greenPixels[ii] + redPixels[ii]));
			}
			
			
			isClump = false;
			newarea = 0;
		}
		
	}//===================================================

	/**
	 * public getter method for clumped nodules array.
	 */
/**indices of clumped nodules. clumped[mixedRoiIndex][0/1] where 0 == red, 1 == green**/
	public int[][] getClumped() {
		return clumped;
	}

/**
 * public setter method for clumped nodules array.
 * @param clumped
 */
	public void setClumped(int[][] clumped) {
		this.clumped = clumped;
	}
	     
	     
}
