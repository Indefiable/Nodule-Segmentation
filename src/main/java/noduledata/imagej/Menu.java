package noduledata.imagej;
import org.scijava.command.Command;


import ij.gui.GenericDialog;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;


/**
 * 
 * This class is an ImageJ Menu class for allowing the user to their files that this program
 * will do the analysis on.
 * 
 * @author Brandin Farris
 *
 */
public class Menu implements Command {
	
	
	protected static final int FOLDER = 1;
	protected static final int IMAGE = 2;
	protected static final int MODEL = 3;
	protected static final int OTHERFILETYPE = 4;
	
	
	protected File file;
    protected File saveFile;
    protected File modelFile;
    protected int redSingle = 3000;
    protected int greenSingle = 3000;
    protected int mixedSingle = 3000;

    
    
    /**
     * displays GUI to let user input their data.
     */
	private void display() {
		// Create the dialog
        GenericDialog gd = new GenericDialog("Nodule Segmentation Plugin");

        // Add a button to select image or folder
        gd.addButton("Select image or folder to analyze", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                selectFiles("image or folder");
            }
        });
        
        // Add a button to select a model file.
        gd.addButton("Select model File", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                selectFiles("model");
            }
        });
        
        // Add a button to select a save file location.
        gd.addButton("Select save File", new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                selectFiles("saveFile");
            }
        });

     
        // Add numeric input fields
        gd.addNumericField("Red nodule size upper bound:", 3000, 0);
        gd.addNumericField("Green nodule size upper bound:", 3000, 0);
        gd.addNumericField("Mixed nodule size upper bound:", 3000, 0);
        
        // Show the dialog to let user interact.
        gd.showDialog();
        
        
        // If user clicked OK.
        if (gd.wasOKed()) {
            // Retrieve the numbers
        	// numbers are retrieved in the order they were coded in.
            double redSingle = gd.getNextNumber();
            
            System.out.println("Red Single: " + redSingle);
            
            double greenSingle = gd.getNextNumber();
            System.out.println("Green Single: " + greenSingle);
            
            double mixedSingle = gd.getNextNumber();
            System.out.println("Mixed Single: " + mixedSingle);
            
            if(file == null || saveFile == null || modelFile == null || redSingle == -1 || greenSingle == -1 || mixedSingle == -1) {
	            System.out.println("Erorr, you must fill in all of the blanks to generate data. Please try again.");
	            display();
            }
            
            int type = getFileType(file);
            if(type == MODEL || type == OTHERFILETYPE) {
            	System.out.println("Error, you must select a folder or image file for the segmentation file.");
	            display();
            }
            
            type = getFileType(saveFile);
            if(type != FOLDER) {
            	System.out.println("Error, you must select a folder for the save file");
	            display();
            }
            
            type = getFileType(modelFile);
            if(type != MODEL) {
            	System.out.println("Error, the model file must be a .model file. You can generate .model files "
            			+ "using Weka's ColorClustering ImageJ plugin. See the github page for more instructions.");
	            display();
            }
            
        }
        else {
        	return;
        }
        
        
	}
	
	
	
	public void run() {
		display();
	}
	
	
	/**
	 * Loads UI to let user select a file, then saves that file in this class object.
	 * 
	 * @param file : String referencing what object they're currently selecting.
	 */
	private void selectFiles(String file) {
		
		System.out.println("FILE :" + file);
		JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Image to load or file to iterate through.");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

        // Add a file filter for image files (you can customize this for specific image types)
        FileNameExtensionFilter imageFilter = new FileNameExtensionFilter("Image Files and Folders", "jpg", "jpeg", "png", "gif", "model");
        fileChooser.setFileFilter(imageFilter);
        
        int result = fileChooser.showOpenDialog(null);
        
        if(result != JFileChooser.APPROVE_OPTION) {
        	System.out.println("Error, invalid option.");
        	return;
        }
        
        
    	switch(file) {
    	
    	case "image or folder" :
    		this.file = fileChooser.getSelectedFile();
    		System.out.println("Chosen: " + this.file.getAbsolutePath());
    		break;
    		
    	case "model":
    		this.modelFile = fileChooser.getSelectedFile();
    		System.out.println("Chosen: " + this.modelFile.getAbsolutePath());
    		break;
    		
    	case "saveFile": 
    		this.saveFile = fileChooser.getSelectedFile();
    		System.out.println("Chosen: " + this.saveFile.getAbsolutePath());
    		break;
    	}
    
        
	}

	
	
	
	 /**
     * Returns:<br>
     * 1 for folder<br>
     * 2 for accepted image type<br>
     * 3 for .model file<br>
     * 4 for any other filetype. <br>
     * Note: These integers are saved as protected Menu variables.
     */
    protected static int getFileType(File file) {
    	String extension = file.getName().substring(file.getName().lastIndexOf(".") + 1);
    	int FILETYPE = 0;
    	if(file.isDirectory()) {
    		FILETYPE = FOLDER;
    	}
    	// all currently accepted image file types.
    	else if(extension.equalsIgnoreCase("jpg") || extension.equalsIgnoreCase("png")
    			|| extension.equalsIgnoreCase("jpeg") || extension.equalsIgnoreCase("gif")
    			|| extension.equalsIgnoreCase("tiff") || extension.equalsIgnoreCase("dcm")){
    		FILETYPE = IMAGE;
    	}
    	else if(extension.equalsIgnoreCase("model")) {
    		FILETYPE = MODEL;
    	}
    	else {
    		System.out.println("Selected file is not a folder or an acceptable "
    				+ "image type. Please ensure the image you're trying to enter"
    				+ "is the correct file type.");
    		FILETYPE = OTHERFILETYPE;
    	}
    	
    	return FILETYPE;
    }
	
	
	
	
	
	
	
	
	
}//end Menu class

