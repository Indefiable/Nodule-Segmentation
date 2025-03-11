package noduledata.imagej;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.StringJoiner;

import javax.swing.ImageIcon;


import org.apache.commons.io.FilenameUtils;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.FreehandRoi;
import ij.gui.ImageCanvas;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.TextRoi;
import ij.gui.Toolbar;
import ij.gui.WaitForUserDialog;
import ij.plugin.frame.RoiManager;

/**
 * 
 * This class was created to allow the user to make manual adjustments to the
 * programs segmentation and outlines of the nodules. It shows the image and
 * allows the user to click on any of the nodules and make adjustments before
 * saving the output.
 * 
 * @author Brandin Farris
 *
 */
public class UserEditsHandler {
	ColorData green;
	ColorData red;
	MixedData mixed;
	private RoiManager manager = new RoiManager();

	ImagePlus imp;// original, unedited image
	ImagePlus annotatedImp;
	int x = 0;
	int y = 0;
	int croppedCenterX;
	int croppedCenterY;
	int delete = -1;
	double widthRatio = -1;
	double heightRatio = -1;
	double scaleRatio = -1;

	int singleNoduleOption = -1;

	// for handling button clicks.

	final int FINISH = 0;
	int BUTTON = -1;
	final int REDRAW = 1;
	final int CORRECT = 2;
	final int REDRAWMIXED = 3;
	final int MAKECLUMP = 4;

	final int RED = 0;
	final int GREEN = 1;
	final int MIXED = 2;

	final int screenwidth = Toolkit.getDefaultToolkit().getScreenSize().width;
	final int screenheight = Toolkit.getDefaultToolkit().getScreenSize().height;
	final int maxWidth = 1000; // Maximum width for the displayed image
	final int maxHeight = 800; // Maximum height for the displayed image

	int noduleCounter = 0;
	int counter = noduleCounter;

	/**
	 * constructor method
	 * 
	 * @param imp   : image we're doing analyiss on.
	 * @param red   : red ColorData object
	 * @param green : green ColorData object
	 * @param mixed : mixed ColorData object.
	 */
	public UserEditsHandler(ImagePlus imp, ColorData red, ColorData green, MixedData mixed) {
		this.imp = imp;
		this.green = green;
		this.red = red;
		this.mixed = mixed;
	}

	/**
	 * 
	 * @return flattened original image outlining the identified nodules and
	 *         numbering them.
	 */
	private ImagePlus GetAnnotatedImage() {
		ImagePlus output = new ImagePlus(imp.getShortTitle() + " annotated", imp.getProcessor());

		Overlay overlay = new Overlay();
		output.setOverlay(overlay);

		TextRoi.setFont("SansSerif", 100, Font.BOLD);
		Font font = new Font("SansSerif", Font.BOLD, 50);

		for (int index = 0; index < red.getRois().length; index++) {
			ShapeRoi roi = red.getRoi(index);
			roi.setPosition(0);
			roi.update(true, false);

			// Add outlines to the ROI
			roi.setStrokeColor(Color.white);
			roi.setStrokeWidth(2);
			output.getOverlay().add(roi);

			if (red.getClump(index) != null) {
				NoduleClump clump = red.getClump(index);
				String label = Integer.toString(clump.numNodules);
				TextRoi textLabel = new TextRoi(roi.getContourCentroid()[0], roi.getContourCentroid()[1], label, font);
				textLabel.setStrokeColor(Color.white);
				textLabel.setStrokeWidth(2);
				output.getOverlay().add(textLabel);
			}
		}

		for (int index = 0; index < green.getRois().length; index++) {

			ShapeRoi roi = green.getRoi(index);
			roi.setPosition(0);
			roi.update(true, false);

			// Add outlines to the ROI
			roi.setStrokeColor(Color.white);
			roi.setStrokeWidth(2);
			output.getOverlay().add(roi);

			if (green.getClump(index) != null) {
				NoduleClump clump = green.getClump(index);
				String label = Integer.toString(clump.numNodules);
				TextRoi textLabel = new TextRoi(roi.getContourCentroid()[0], roi.getContourCentroid()[1], label, font);
				textLabel.setStrokeColor(Color.white);
				textLabel.setStrokeWidth(2);
				output.getOverlay().add(textLabel);
			}

		}

		for (ShapeRoi mixedRoi : mixed.getRois()) {
			int index = mixed.getIndex(mixedRoi);

			if (!(mixed.getClumped()[index][0] == -1 && mixed.getClumped()[index][1] == -1)) {
				continue;
			}
			mixedRoi.setPosition(0);
			mixedRoi.update(true, false);

			// Add outlines to the ROI
			mixedRoi.setStrokeColor(Color.white);
			mixedRoi.setStrokeWidth(2);

			output.getOverlay().add(mixedRoi);
		}

		output.getOverlay().drawNames(false);
		output.getOverlay().drawBackgrounds(true);

		try {
			output.flattenStack();
		} catch (Exception e) {
			System.out.println("No ROI's to overlay.");
		}

		return output;
	}

	/**
	 * returns the scaled image icon according to the given newWidth and newHeight
	 */
	public ImageIcon scaleImage(ImageIcon imp, int newWidth, int newHeight) {
		Image originalImage = imp.getImage();

		BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);

		Graphics2D g2d = scaledImage.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
		g2d.dispose();

		return new ImageIcon(scaledImage);
	}

	/**
	 * Method that's called to initialize part of the program allowing the user to
	 * make manual changes.
	 */
	public void run(String saveFile) {

		annotatedImp = GetAnnotatedImage();

		Display(annotatedImp, saveFile);

		return;
	}

	/**
	 * Displays the current annotated image and waits for user interaction.
	 */
	private void Display(ImagePlus animp, String saveFile) {

		GenericDialogPlus gd = new GenericDialogPlus("Click on any Errors to Adjust.");
		gd.addMessage("This image is displaying the nodules we've found. If any are wrong, you can "
				+ "click on them to adjust them.");

		int imageWidth = animp.getWidth();
		int imageHeight = animp.getHeight();

		if (imageWidth > maxWidth || imageHeight > maxHeight) {
			// Scale down the image while preserving the aspect ratio
			double widthRatio = (double) maxWidth / imageWidth;
			double heightRatio = (double) maxHeight / imageHeight;
			double scaleRatio = Math.min(widthRatio, heightRatio);
			this.widthRatio = widthRatio;
			this.heightRatio = heightRatio;
			this.scaleRatio = scaleRatio;
			imageWidth = (int) (imageWidth * scaleRatio);
			imageHeight = (int) (imageHeight * scaleRatio);
			animp = animp.resize(imageWidth, imageHeight, "bilinear");
			
		} else {
			
		}

		int tx = (int) screenwidth;
		int ty = (int) screenheight;
		int height = imageHeight + 100;
		int width = imageWidth + 100;
		int newx = (tx - width) / 2;
		int newy = (ty - height) / 2;

		ImageCanvas canvas = new ImageCanvas(animp);
		gd.add(canvas);

		canvas.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				x = e.getPoint().x;
				y = e.getPoint().y;
				noduleCounter++;
				gd.setVisible(false);
				
			}
		});

		/*
		 * For some reason, the first button added isn't registered, but the second is,
		 * so adding a null button makes the finish button appear and function as
		 * intended.
		 */
		gd.addButton("temp", gd);

		gd.addButton("Finish", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				BUTTON = FINISH;
				gd.dispose();
			}
		});

		gd.setBounds(newx, newy, width, height);
		gd.toFront();
		gd.setVisible(true);

		if (noduleCounter != counter) {
			counter++;
			System.out.println("POINT CLICKED: " + x + ", " + y);
			Point p = new Point(x, y);
			mouseClicked(p, (1 / scaleRatio), animp);
			annotatedImp = GetAnnotatedImage();
			gd.removeAll();
			Display(annotatedImp, saveFile);
		}

		else if (BUTTON != -1) {

			buttonClicked(saveFile);

			if (BUTTON == FINISH) {
				this.red = null;
				this.mixed = null;
				this.green = null;
				this.annotatedImp = null;
				System.gc();
				return;
			}

			BUTTON = -1;
			gd.removeAll();
			Display(annotatedImp, saveFile);
		}
	}

	/**
	 * Method for handling when the user has clicked on one of the available buttons
	 * on the user corrections menu.
	 * 
	 * @param saveFile : string object containing the user-specified save location.
	 */
	private void buttonClicked(String saveFile) {

		if (BUTTON == -1) {
			IJ.log("Error, illegal state.");
			return;
		}
		switch (BUTTON) {

		case (FINISH):

			String selectedFolder = saveFile + "\\";
			
			saveCSV(selectedFolder);
			saveCombinedLabels(selectedFolder);
			break;
		}

	}

	/**
	 * Gets the index of the nodule the user clicked on.
	 * 
	 * @param color : the object pertaining to the nodule type the user clicked on.
	 * @param p     : pixel location that the user clicked on.
	 * @return : the index or -1 if no index was found.
	 */
	private int getIndex(ColorData color, Point p) {

		for (ShapeRoi roi : color.getRois()) {
			ShapeRoi clicked = new ShapeRoi(new OvalRoi(p.x, p.y, 2, 2));
			if (clicked.and(roi).getContainedPoints().length != 0) {
				System.out.println("Found Roi that contains point.");
				return color.getIndex(roi);
			}
		}

		return -1;
	}

	/**
	 * Handles when the user clicks anywhere on the GUI.<br>
	 * Checks if the user clicked on any nodule on the image, and if so, calls a sub
	 * menu to allow user to edit that nodule.
	 * 
	 * @param p1         : pixel location clicked.
	 * @param scaleRatio : ratio for converting between screen location and image
	 *                   location.
	 * @param animp      : image we're doing analysis on.
	 */
	protected void mouseClicked(Point p1, double scaleRatio, ImagePlus animp) {

		int x = (int) (p1.x * scaleRatio);
		int y = (int) (p1.y * scaleRatio);
		boolean isClump;
		Point p = new Point(x, y);
		System.out.println("Clicked coordinate: (" + p.x + ", " + p.y + ").");
		ShapeRoi newRoi = null;

		int index = -1;
		String color = "None";
		ShapeRoi roi = null;

		if (getIndex(red, p) != -1) {
			index = getIndex(red, p);
			System.out.println("Red nodule clicked.");
			color = "red";
			roi = red.getRoi(index);
		}

		else if (getIndex(green, p) != -1) {
			index = getIndex(green, p);
			System.out.println("Green nodule clicked.");
			color = "green";
			roi = green.getRoi(index);
		}

		else if (getIndex(mixed, p) != -1) {
			index = getIndex(mixed, p);
			System.out.println("Mixed nodule clicked.");
			color = "mixed";
			roi = mixed.getRoi(index);
		}

		else {
			System.out.println("No nodule found where you clicked.");
			return;
		}

		GenericDialogPlus gdd = new GenericDialogPlus("What do you want to do with this " + color + " ROI?");

		ImagePlus croi = getImageFromRoi(roi, this.imp);
		gdd.addImage(croi);
		gdd.addMessage("Do you want to redraw this ROI, or delete it?");

		gdd.addButton("Delete the ROI", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				delete = 1;
				gdd.dispose();
			}
		});

		gdd.addButton("Edit the ROI", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				delete = -1;
				gdd.dispose();
			}
		});

		int newx = (screenwidth - 600) / 2;
		int newy = (screenheight - 150) / 2;
		gdd.setBounds(newx, newy, 600, 150 + croi.getHeight());
		gdd.setVisible(true);
		gdd.toFront();

		if (color.equalsIgnoreCase("red")) {

			if (red.getClump(index) == null) {
				isClump = false;
			} else {
				isClump = true;
			}

			if (delete == 1) {
				red.removeRoi(red.getRoi(index));

				int numRois = mixed.getRois().length - 1;

				for (int ii = numRois; ii > -1; ii--) {
					if (mixed.getClumped()[ii][0] == index) {
						mixed.removeRoi(mixed.getRoi(ii));
					}
				}
				adjustMixedClumped(index, RED);

				return;
			}

			if (isClump) {
				int[] mixedIndex = mixedClump(index, RED);
				roi = red.getRoi(index);

				if (mixedIndex.length > 0) {
					noduleClumpClicked(red, roi, index, scaleRatio, true, mixedIndex);
				} else {
					noduleClumpClicked(red, roi, index, scaleRatio, false, null);
				}

				return;
			}

			// they didn't delete the nodule and it's not a clump:
			SingleNoduleClicked(roi, color, newRoi, index);

		}

		if (color.equalsIgnoreCase("green")) {

			if (delete == 1) {
				green.removeRoi(green.getRoi(index));

				int numRois = mixed.getRois().length - 1;
				for (int ii = numRois; ii > -1; ii--) {
					if (mixed.getClumped()[ii][1] == index) {
						mixed.removeRoi(mixed.getRoi(ii));
					}
				}
				adjustMixedClumped(index, GREEN);
				return;
			}
			if (green.getClump(index) == null) {
				isClump = false;
			} else {
				isClump = true;
			}

			if (isClump) {
				int[] mixedIndex = mixedClump(index, GREEN);
				roi = green.getRoi(index);
				if (mixedIndex.length > 0) {
					noduleClumpClicked(green, roi, index, scaleRatio, true, mixedIndex);
				} else {
					noduleClumpClicked(green, roi, index, scaleRatio, false, null);
				}
				return;
			}

			// they didn't delete the nodule and it's not a clump:
			SingleNoduleClicked(roi, color, newRoi, index);

			if (singleNoduleOption == REDRAW) {
				newRoi = redraw(index, green.COLOR, scaleRatio);
				if (newRoi == null) {
					return;
				}
				update(GREEN, newRoi, index);
				return;
			}

			else if (singleNoduleOption == REDRAW) {

				return;
			}

		}

		if (color.equalsIgnoreCase("mixed")) {
			if (delete == 1) {
				mixed.removeRoi(mixed.getRoi(index));
				return;
			}

			newRoi = redraw(index, new int[] { 255, 255, 255 }, scaleRatio);
			if (newRoi == null) {
				return;
			}
			update(MIXED, newRoi, index);
			return;
		}

		return;
	}

	/**
	 * method for handling when a single (not clumped) nodule has been clicked on.
	 * Opens menu for allowing user to make changes to the clicked nodule.
	 * 
	 * @param roi    : roi/nodule clicked on.
	 * @param color  : color of the nodule roi/nodule clicked on.
	 * @param newRoi : roi object for if the user redraws the nodule outline.
	 * @param index  : index of the nodule.
	 */
	private void SingleNoduleClicked(ShapeRoi roi, String color, ShapeRoi newRoi, int index) {

		GenericDialogPlus gdd = new GenericDialogPlus("What do you want to do with this " + color + " ROI?");
		ImagePlus croi = getImageFromRoi(roi, this.imp);

		gdd.addImage(croi);
		gdd.addMessage("Do you want to Redraw this ROI or make it a clump.");

		gdd.addButton("	Make ROI a Clump", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				singleNoduleOption = MAKECLUMP;
				gdd.dispose();
			}
		});

		gdd.addButton("Redraw the ROI", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				singleNoduleOption = REDRAW;
				gdd.dispose();
			}
		});

		int newx = (screenwidth - 600) / 2;
		int newy = (screenheight - 150) / 2;
		gdd.setBounds(newx, newy, 600, 150 + croi.getHeight());
		gdd.setVisible(true);
		gdd.toFront();

		if (color.equalsIgnoreCase("red")) {
			if (singleNoduleOption == REDRAW) {
				newRoi = redraw(index, red.COLOR, scaleRatio);
				if (newRoi == null) {
					return;
				}
				update(RED, newRoi, index);
				return;
			}

			else if (singleNoduleOption == MAKECLUMP) {

				return;
			}
		}

		else if (color.equalsIgnoreCase("green")) {
			if (singleNoduleOption == REDRAW) {
				newRoi = redraw(index, green.COLOR, scaleRatio);
				if (newRoi == null) {
					return;
				}
				update(GREEN, newRoi, index);
				return;
			}

			else if (singleNoduleOption == MAKECLUMP) {

				return;
			}
		}

	}

	/**
	 * clumped[][] indices in mixed.getClumped() are off by one when an ROI in
	 * colored.ROIs() is deleted and a mixed nodule is in a clumped roi after that
	 * in color.getRois();
	 * 
	 * @param index
	 * @param color
	 */
	private void adjustMixedClumped(int index, int color) {
		//

		int numRois = mixed.getRois().length - 1;

		for (int ii = numRois; ii > -1; ii--) {
			if (color == RED) {
				if (mixed.getClumped()[ii][0] > index) {
					mixed.getClumped()[ii][0]--;
				}
			}

			else {
				if (mixed.getClumped()[ii][1] > index) {
					mixed.getClumped()[ii][1]--;
				}
			}
		}

	}

	/**
	 * Checks if any mixed nodules are in the clicked roi, which is at the given
	 * index.
	 */
	private int[] mixedClump(int index, int color) {
		ArrayList<Integer> mixedIndices = new ArrayList<>();

		if (mixed.getClumped() == null) {
			int[] temp = new int[0];
			return temp;
		}

		int clumped[][] = mixed.getClumped();

		for (int nod = 0; nod < clumped.length; nod++) {

			if (color == RED) {
				if (clumped[nod][0] == index) {
					mixedIndices.add(nod);
				}
			}

			else if (color == GREEN) {
				if (clumped[nod][1] == index) {
					mixedIndices.add(nod);
				}
			}
		}

		int[] intArray = new int[mixedIndices.size()];
		for (int i = 0; i < mixedIndices.size(); i++) {
			intArray[i] = mixedIndices.get(i);
		}

		return intArray;
	}

	/**
	 * Allows the user to redraw the clicked roi, which is at the given index.
	 * 
	 * @param index      : index of the clicked roi.
	 * @param colour     : color of the clicked roi.
	 * @param scaleRatio : ratio for convertering between screen location and image
	 *                   location.
	 * @return : user-drawn roi.
	 */
	ShapeRoi redraw(int index, int[] colour, double scaleRatio) {

		ShapeRoi roi;
		int color = -1;
		if (colour[0] == 255 && colour[1] == 0) {
			color = RED;
		} else if (colour[0] == 0 && colour[1] == 255) {
			color = GREEN;
		} else {
			color = MIXED;
		}

		if (color == RED) {
			roi = red.getRoi(index);
		} else if (color == GREEN) {
			roi = green.getRoi(index);
		} else if (color == MIXED) {
			roi = mixed.getRoi(index);
		}

		else {
			System.out.println("Error, could not find Roi.");
			return null;
		}

		double[] topLeft = { roi.getBounds().x, roi.getBounds().y };
		int roiWidth = roi.getBounds().width;
		int roiHeight = roi.getBounds().height;
		int dimsX = (int) (2 * roiWidth);
		int dimsY = (int) (2 * roiHeight);
		int boxX = (int) (topLeft[0] - 0.25 * dimsX);
		int boxY = (int) (topLeft[1] - 0.25 * dimsY);

		ImagePlus image = getImageFromRoi(roi, this.imp);

		image.show();
		image.getWindow().toFront();

		@SuppressWarnings("unused")
		Toolbar toolbar = new Toolbar();

		Toolbar.getInstance().setTool(Toolbar.FREEROI);

		FreehandRoi freehandRoi = new FreehandRoi(0, 0, imp);

		freehandRoi.setStrokeColor(Color.YELLOW);

		image.setRoi(freehandRoi);

		// Wait for the user to finish drawing the ROI
		new WaitForUserDialog("Please redraw the ROI, then press OK.").show();

		if (image.getRoi() == null || image.getRoi().getContainedPoints().length == 0) {
			image.close();
			IJ.log("no roi drawn. cancelling action.");
			return null;
		}

		ShapeRoi newRoi = new ShapeRoi(image.getRoi());

		int newx = newRoi.getBounds().x + boxX;
		int newy = newRoi.getBounds().y + boxY;

		newRoi.setLocation(newx, newy);

		image.close();
		return newRoi;
	}

	/**
	 * Creates a zoomed in image of the given nodule. Used for display purposes.
	 * 
	 * @param roi Given Roi to zoom in on
	 * @param imp image that contains the given roi.
	 * @return a close up of the given roi
	 */
	public static ImagePlus getImageFromRoi(ShapeRoi roi, ImagePlus imp) {
		ImagePlus image = new ImagePlus(imp.getShortTitle(), imp.getProcessor());

		double[] topLeft = { roi.getBounds().x, roi.getBounds().y };
		int roiWidth = roi.getBounds().width;
		int roiHeight = roi.getBounds().height;

		int dimsX = (int) (2 * roiWidth);
		int dimsY = (int) (2 * roiHeight);
		int boxX = (int) (topLeft[0] - 0.25 * dimsX);
		int boxY = (int) (topLeft[1] - 0.25 * dimsY);

		Rectangle imageBounds = new Rectangle(boxX, boxY, dimsX, dimsY);

		image.setRoi(imageBounds);
		image = image.crop();
		Rectangle boundingBox = roi.getBounds();
		boundingBox.setLocation((int) (0.25 * dimsX), (int) (0.25 * dimsY));

		Overlay boxOverlay = new Overlay();
		image.setOverlay(boxOverlay);

		image.getOverlay().add(new Roi(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height));

		final int screenwidth = Toolkit.getDefaultToolkit().getScreenSize().width;
		final int screenheight = Toolkit.getDefaultToolkit().getScreenSize().height;

		// These are the largest because the UI width will be (screenwidth-600)/2), and
		// we want
		// 100 pixel leeway between image and boundary of UI. More for the y axis to
		// make the options visible.
		// if the image is too big, scale it down while maintaining aspect ratio.
		int largestAcceptableWidth = ((screenwidth - 600) / 2) - 100;
		int largestAcceptableHeight = ((screenheight - 150) / 2) - 200;
		double aspectRatio = ((double) image.getHeight()) / ((double) image.getWidth());

		double xDiff = 0;
		double yDiff = 0;
		if (image.getWidth() > largestAcceptableWidth) {
			xDiff = image.getWidth() - largestAcceptableWidth;
		}
		if (image.getHeight() > largestAcceptableHeight) {
			yDiff = image.getHeight() - largestAcceptableHeight;
		}

		if (xDiff != 0 && yDiff == 0) {
			int newHeight = (int) image.getHeight();
			int newWidth = (int) (newHeight * 1 / aspectRatio);
			image = image.resize(newWidth, newHeight, "none");
		}

		else if (xDiff == 0 && yDiff != 0) {
			int newWidth = (int) image.getWidth();
			int newHeight = (int) (newWidth * aspectRatio);
			image = image.resize(newWidth, newHeight, "none");
		}

		else if (xDiff != 0 && yDiff != 0) {
			if (xDiff > yDiff) {
				int newHeight = (int) largestAcceptableHeight;
				int newWidth = (int) (newHeight * 1 / aspectRatio);
				image = image.resize(newWidth, newHeight, "none");
			}

			if (yDiff > xDiff) {
				int newWidth = largestAcceptableWidth;
				int newHeight = (int) (newWidth * aspectRatio);
				image = image.resize(newWidth, newHeight, "none");
			}
		}

		try {
			image.flattenStack();
		} catch (Exception e) {
			System.out.println("Error, could not find bounding box.");
		}

		return image;
	}

	/**
	 * updates the roi at the given index of the given color.
	 * 
	 * @param color : color of the new roi.
	 * @param roi   : roi to replace current.
	 * @param index : index of the roi being replaced.
	 */
	private void update(int color, ShapeRoi roi, int index) {

		switch (color) {

		case RED:
			red.setRoi(index, roi);
			break;

		case GREEN:
			green.setRoi(index, roi);
			break;

		case MIXED:

			mixed.setRoi(index, roi);

			break;
		}

		return;
	}

	/**
	 * prompts user whether they want to redraw nodules or correct the number of
	 * detected nodules in the clump.
	 */
	private void noduleClumpClicked(ColorData color, ShapeRoi roi, int index, double scaleRatio, boolean containsMixed,
			int[] mixedIndices) {
		GenericDialogPlus gd = new GenericDialogPlus("Nodule Options");
		gd.addMessage("Would you like to Redraw the nodules in in this ROI or " + "just correct the number of nodules");

		ImagePlus croi = getImageFromRoi(roi, this.imp);
		gd.addImage(croi);
		gd.addButton("Redraw Nodules", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				BUTTON = REDRAW;
				gd.dispose();
			}
		});

		if (containsMixed) {
			gd.addButton("Redraw mixed Nodule", new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					BUTTON = REDRAWMIXED;
					gd.dispose();
				}
			});

		}
		gd.addButton("Correct Number of Nodules", new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				BUTTON = CORRECT;
				gd.dispose();
			}
		});

		int newx = (screenwidth - 600) / 2;
		int newy = (screenheight - 150) / 2;
		gd.setBounds(newx, newy, 600, 170 + croi.getHeight());
		gd.setVisible(true);
		gd.toFront();
		int newNumNodules;
		int currentNumNodules;
		switch (BUTTON) {

		case CORRECT:
			IJ.log("correct");
			currentNumNodules = color.getClump(index).numNodules;
			newNumNodules = color.getClump(index).correctClump(getImageFromRoi(roi, this.imp));
			color.getArea()[index][0] = newNumNodules;
			color.numNodules += newNumNodules - currentNumNodules;
			break;

		case REDRAW:

			IJ.log("redraw");
			currentNumNodules = color.getClump(index).numNodules;
			newNumNodules = color.getClump(index).correctClump(getImageFromRoi(roi, this.imp));
			color.getArea()[index][0] = newNumNodules;
			color.numNodules -= currentNumNodules;
			redrawClumped(index, color, scaleRatio);

			if (color.COLOR[0] == 255) {
				adjustMixedClumped(index, RED);
			} else {
				adjustMixedClumped(index, GREEN);
			}

			break;

		case REDRAWMIXED:
			IJ.log("redraw");

			for (int ii = 0; ii < mixedIndices.length; ii++) {
				redrawMixedFromClump(index, color, scaleRatio, mixedIndices[ii]);
			}
			System.out.println("=================");
		}

		BUTTON = -1;
		return;
	}

	/**
	 * Allows the user to redraw the mixed nodule that is in a nodule clump.
	 * 
	 * @param index      : index of the roi.
	 * @param color      : color of the roi that contains the mixed nodule in it's
	 *                   clump.
	 * @param scaleRatio : ratio for converting between screen and image locations.
	 * @param mixedIndex : index in the mixedData roi array.
	 */
	private void redrawMixedFromClump(int index, ColorData color, double scaleRatio, int mixedIndex) {
		int numMixed = userInput(getImageFromRoi(color.getRoi(index), this.imp));

		for (int ii = 0; ii < numMixed; ii++) {
			ShapeRoi newRoi = redraw(index, color.COLOR, scaleRatio);

			ShapeRoi clump = color.getRoi(index);
			if (ii == 0) {
				mixed.setRoi(mixedIndex, newRoi);
				mixed.getClumped()[mixedIndex][0] = -1;
				mixed.getClumped()[mixedIndex][1] = -1;
			}

			else {
				mixed.addRoi(newRoi);
			}

			clump = clump.xor(newRoi);
			clump = clump.not(newRoi);
			color.setRoi(index, clump);

			color.getClump(index).hasMixed = false;
			color.getClump(index).roi = clump;
			double newArea = clump.getContainedPoints().length;
			color.getClump(index).numNodules--;
			color.numNodules--;
			color.getClump(index).area = newArea;
		}

	}

	/**
	 * continuously allows the user to outline the individual ROI's that make the
	 * clump of interest. Also allows user to stop pre-emptively if they only want
	 * to outline some of the ROI's (such as the mixed nodule ROI's) without the
	 * other ones.
	 */
	private void redrawClumped(int index, ColorData color, double scaleRatio) {

		int numnods = color.getClump(index).numNodules;

		int counter = 0;
		ShapeRoi newRoi;

		// after each redrawing of the ROI, want to :
		// 1. remove that piece of the ROI from the clump.
		// 2. add that ROI as a new ROI under the given color.
		// 3. check if there are more ROI's that can be drawn.

		while (true) {
			counter++;
			newRoi = redraw(index, color.COLOR, scaleRatio);

			ShapeRoi clump = color.getRoi(index);

			color.addRoi(newRoi);

			clump = clump.xor(newRoi);

			color.setRoi(index, clump);
			double newArea = clump.getContainedPoints().length;
			color.getClump(index).numNodules--;
			color.getClump(index).area = newArea;

			if (counter == numnods) {
				color.removeRoi(clump);
				break;
			}
		}

		return;
	}

	/**
	 * Prompts the user to correct the number of nodules within the clump.
	 * 
	 * @param image : image with roi of interest outlined.
	 * @return : the number of nodules thought to be in the outlined Roi.
	 */
	private int userInput(ImagePlus image) {
		boolean retry = true;
		boolean scale = false;
		ImagePlus scaledImage = new ImagePlus();
		while (retry) {

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

			GenericDialogPlus gd = new GenericDialogPlus("Correcting Mixed Nodule Count");

			if (scale) {
				gd.addImage(new ImageIcon(scaledImage.getBufferedImage()));
			} else {
				gd.addImage(new ImageIcon(image.getBufferedImage()));
			}
			gd.addNumericField("Correct number of mixed nodules: ", 0);
			gd.showDialog();

			int numNodules = (int) gd.getNextNumber();

			if (numNodules > 0) {
				return numNodules;
			} else {
				IJ.log("Error: please enter a positive number.");
				retry = true;
			}

		}

		return -2;
	}

	/**
	 * Saves two images: original image with ROI's outlined and labeled, and a .Tif
	 * file with all the ROI outline data. Labeling starts with the red nodules,
	 * then green, then mixed. Each nodule is named "[color][number of nodules]"
	 */
	private void saveCombinedLabels(String saveLocation) {
		ImagePlus image = this.imp;

		manager.reset();
		manager.setVisible(true);
		manager.setEnabled(true);

		int ii = 0;

		image.show();
		try {
			for (Roi red1 : red.getRois()) {
				// == null ==> not a clump
				if (red.getClump(ii) == null) {
					red1.setName("r " + "1");
				} else {
					red1.setName("r " + red.getClump(ii).numNodules);
				}
				manager.add(image, red1, ii++);
			}
		} catch (Exception e) {
			System.out.println("No red ROI's found.");
		}

		try {
			System.out.println(green.numNodules);
			System.out.println(green.getRois().length);
			for (Roi green1 : green.getRois()) {
				// == null ==> not a clump
				if (green.getClump(ii) == null) {
					green1.setName("g " + "1");
				} else {
					green1.setName("g " + green.getClump(ii).numNodules);
				}
				manager.add(image, green1, ii++);
			}
		} catch (Exception e) {
			System.out.println("No green ROI's found.");
		}
		try {
			for (Roi mixedRoi : mixed.getRois()) {
				int index = mixed.getIndex(mixedRoi);
				if (mixed.getClumped()[index][0] == -1 && mixed.getClumped()[index][1] == -1) {
					mixedRoi.setName("m " + "1");
					manager.add(image, mixedRoi, ii++);
				}
			}
		} catch (Exception e) {
			System.out.println("No Mixed ROI's found.");
		}

		Overlay overlay = new Overlay();
		image.setOverlay(overlay);

		System.out.println(manager.getCount());
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

		saveLocation = saveLocation + image.getTitle();
		IJ.saveAs(image, "Tif", saveLocation + ".Tif");
		image.setOverlay(overlay);
		image.getOverlay().setLabelFontSize(20, "bold");
		image.getOverlay().drawLabels(true);
		image.getOverlay().setLabelColor(Color.white);
		image.getOverlay().setLabelFont(overlay.getLabelFont());

		IJ.saveAs("jpeg", saveLocation + "_Annotated");

		image.close();
		System.out.println("All Roi's added to original image.");

	}

	// =======================
	/**
	 * saves the generated data as a CSV file.
	 */
	private void saveCSV(String saveLocation) {


		int[][] redAreas = red.getArea();
		int[][] greenAreas = green.getArea();
		int[][] mixedAreas = mixed.getArea();

		// SaveDialog saver = new SaveDialog("Save CSV File",
		// FilenameUtils.removeExtension(image.getTitle())+ "_data.csv", "");
		// String dir = saver.getDirectory();
		// String name = saver.getFileName();

		String dir = saveLocation;
		String name = FilenameUtils.removeExtension(imp.getTitle());

		if (dir == null || name == null) {
			IJ.log("saving cancelled");
			return;
		}

		String save = dir + name + ".csv";

		int numRedNods = red.numNodules;

		int numGreenNods = green.numNodules;

		int numMixedNods = mixed.numNodules;

		try {
			for (NoduleClump clump : red.getClumps()) {
				if (clump.hasMixed) {
					numRedNods--;
					clump.numNodules--;
					redAreas[clump.index][0]--;
				}
			}
		} catch (Exception e) {
			System.out.println("No red nodule Clumps");
		}

		try {
			for (NoduleClump clump : green.getClumps()) {
				if (clump.hasMixed) {
					numGreenNods--;
					clump.numNodules--;
					greenAreas[clump.index][0]--;
				}
			}
		} catch (Exception e) {
			System.out.println("No green nodule clumps.");
		}

		if (numRedNods < 0) {
			numRedNods = 0;
		}
		if (numGreenNods < 0) {
			numGreenNods = 0;
		}
		if (numMixedNods < 0) {
			numMixedNods = 0;
		}

		int numRedRois = 0;
		int numGreenRois = 0;

		if (red.getRois() == null) {
			numRedRois = 0;
		} else {
			numRedRois = red.getRois().length;
		}
		if (green.getRois() == null) {
			numGreenRois = 0;
		} else {
			numGreenRois = green.getRois().length;
		}

		String[] header = { "Roi", "Area", "Color", "Red Pixel Count", "Green Pixel Count", "circularity" };
		String[][] mat = new String[numRedNods + numGreenNods + numMixedNods + 1][6];
		mat[0] = header;

		int roiCounter = 0;
		for (int ii = 1; ii < numRedNods + 1; ii++) {

			if (redAreas[0][1] == 0) {
				break;
			}
			if (redAreas[roiCounter][0] == 1) {
				mat[ii][0] = Integer.toString(roiCounter + 1);
				mat[ii][1] = Integer.toString(redAreas[roiCounter][1]);
				mat[ii][2] = "Red";
				mat[ii][3] = Integer.toString(redAreas[roiCounter][1]);
				mat[ii][4] = "0";
				mat[ii][5] = Double.toString(red.getCircularity(roiCounter));
			} else {
				System.out.println("multiple red nodules in this roi.");
				int cc = 1;
				for (int jj = 0; jj < redAreas[roiCounter][0]; jj++) {
					mat[ii][0] = Integer.toString(roiCounter + 1) + "_" + cc++;
					mat[ii][1] = Integer.toString(redAreas[roiCounter][1] / redAreas[roiCounter][0]);
					mat[ii][2] = "Red";
					mat[ii][3] = Integer.toString(redAreas[roiCounter][1] / redAreas[roiCounter][0]);
					mat[ii][4] = "0";
					mat[ii][5] = Double.toString(red.getCircularity(roiCounter));
					ii++;
				}
				ii -= 1;
			}

			roiCounter++;
		}

		roiCounter = 0;
		for (int ii = 0; ii < numGreenNods; ii++) {

			if (greenAreas[0][1] == 0) {
				break;
			}

			int matIndex = ii + numRedNods + 1;

			if (greenAreas[roiCounter][0] == 1) {
				mat[matIndex][0] = Integer.toString(roiCounter + numRedRois + 1);
				mat[matIndex][1] = Integer.toString(greenAreas[roiCounter][1]);
				mat[matIndex][2] = "Green";
				mat[matIndex][3] = "0";
				mat[matIndex][4] = Integer.toString(greenAreas[roiCounter][1]);
				mat[matIndex][5] = Double.toString(green.getCircularity(roiCounter));
			}

			else {
				System.out.println("multiple green nodules in this roi.");
				int pause = roiCounter;
				int pause2 = roiCounter + numRedRois + 1;
				int numNodulesInClump = greenAreas[roiCounter][0];
				int cc = 1;
				for (int jj = 0; jj < numNodulesInClump; jj++) {

					matIndex = ii + numRedNods + 1;
					mat[matIndex][0] = Integer.toString(pause2) + "_" + cc++;
					mat[matIndex][1] = Integer.toString(greenAreas[pause][1] / greenAreas[roiCounter][0]);
					mat[matIndex][2] = "Green";
					mat[matIndex][3] = "0";
					mat[matIndex][4] = Integer.toString(greenAreas[pause][1] / greenAreas[roiCounter][0]);
					mat[matIndex][5] = Double.toString(green.getCircularity(pause));
					ii++;
				}
				ii -= 1;
			}
			roiCounter++;

		}

		roiCounter = 0;
		for (int ii = 0; ii < numMixedNods; ii++) {
			if (mixedAreas[0][1] == 0) {
				break;
			}
			int matIndex = ii + numRedNods + numGreenNods + 1;
			int clumpIndex = Integer.max(mixed.getClumped()[roiCounter][0], mixed.getClumped()[roiCounter][1]);

			if (clumpIndex == -1) {
				mat[matIndex][0] = Integer.toString(roiCounter + numRedRois + numGreenRois + 1);
			}

			else {
				if (mixed.getClumped()[roiCounter][0] > mixed.getClumped()[roiCounter][1]) {
					mat[matIndex][0] = Integer.toString(clumpIndex + 1) + "_"
							+ Integer.toString((red.getClump(clumpIndex).numNodules) + 1);
				} else {
					mat[matIndex][0] = Integer.toString(numRedRois + clumpIndex + 1) + "_"
							+ Integer.toString((green.getClump(clumpIndex).numNodules) + 1);
				}
			}

			mat[matIndex][1] = Integer.toString(mixedAreas[roiCounter][1]);
			mat[matIndex][2] = "Mixed";
			mat[matIndex][3] = Integer.toString(mixed.getPixelArea(ii, "red"));
			mat[matIndex][4] = Integer.toString(mixed.getPixelArea(ii, "green"));
			roiCounter++;
		}

		try (FileWriter writer = new FileWriter(save)) {
			StringJoiner comma = new StringJoiner(",");
			for (String[] row : mat) {
				comma.setEmptyValue("");
				comma.add(String.join(",", row));
				writer.write(comma.toString());
				writer.write(System.lineSeparator());
				comma = new StringJoiner(",");
			}

			writer.flush();
			writer.close();
			System.out.println("=================");
			System.out.println("CSV FILE SAVED.");
			System.out.println("=================");

		} catch (IOException e) {
			System.out.println("=============================");
			System.err.println("Error writing CSV file: " + e.getMessage());
			System.out.println("============================");
		}
	}

}
