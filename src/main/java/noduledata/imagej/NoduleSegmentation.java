/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package noduledata.imagej;

import java.io.File;
import java.util.ArrayList;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import ij.IJ;
import ij.ImagePlus;

import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import trainableSegmentation.unsupervised.ColorClustering;
import trainableSegmentation.unsupervised.ColorClustering.Channel;


/**
 * 
 * 
 * @author Brandin Farris
 *
 */
@Plugin(type = Command.class, menuPath = "Plugins>Nodule Segmentation")
public class NoduleSegmentation implements Command {
	
	private final int FOLDER = 1;
	private final int IMAGE = 2;
	private final int MODEL = 3;
	private final int OTHERFILETYPE = 4;
	public static ImagePlus image;
	
    @Parameter
    private LogService logService;

    @Parameter
    private UIService uiService;

    @Parameter
    private OpService opService;
    
   // @Parameter(label = "Image to load or file to iterate through.")
   // private File file;
    
    @Parameter(label = "cluster model to use for segmentation.")
    private File modelFile;
    
    
    /**
     * This method executes the image analysis.
     * 
     * @param image : image to run the data analysis on.
     * @param model : path file to selected .model file
     */
    
    private void execute(ImagePlus image, String model) {
    	
    	ArrayList<Channel> channels = new ArrayList<Channel>(); // channels to use when segmenting.
    	channels.add(Channel.Red);
    	channels.add(Channel.Green);
   
		
		if(image.getType() != ImagePlus.COLOR_RGB) {
			image = new ImagePlus(image.getTitle(), image.getProcessor().convertToRGB());
		}
		
		image.setRoi(1200, 750, 3600, 2500); // cropping image to center of image and halving the size.
		image = image.crop();
		
		NoduleSegmentation.image= image;
		ColorClustering cluster = new ColorClustering(image);
		cluster.loadClusterer(modelFile.getAbsolutePath());
		cluster.setChannels(channels);
	
	try {
			NoduleData noduledata = new NoduleData(cluster);
			noduledata.run();
		
	}	catch(Exception e) {
		e.printStackTrace();
	}
    }

    /**
     * Returns 1 for folder, 2 for accepted 
image type, 3 for .model file, or 4 for any other filetype. 
     */
    private int getFileType(File file) {
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
    		System.out.println("Selected file ir not a folder or an acceptable "
    				+ "image type. Please ensure the image you're trying to enter"
    				+ "has the correct end abbreviation.");
    		FILETYPE = OTHERFILETYPE;
    	}
    	
    	return FILETYPE;
    }
    
    @Override
    public void run() {
    	
    	
    	/*
    	File file = new File("D:\\1EDUCATION\\aRESEARCH\\16Fluorescence");
    	
    	for (File species : file.listFiles()) {
    	String thisSpecies = species.getName();
    		
    		
    	for(File type : species.listFiles()) {
    	String thisType = type.getName();
    	
    	for(File im : type.listFiles()) {
    		ImagePlus image;
    		try {
    			 image = new ImagePlus(im.getAbsolutePath());
    		}
    		catch(Exception e) {
    			IJ.log("===================");
    			IJ.log("Not an image file.");
    			IJ.log("==================");
    			continue;
    		}
    		
    	ArrayList<Channel> channels = new ArrayList<Channel>(); // channels to use when segmenting.
    	channels.add(Channel.Red);
    	channels.add(Channel.Green);
		
		if(image.getType() != ImagePlus.COLOR_RGB) {
			image = new ImagePlus(image.getTitle(), image.getProcessor().convertToRGB());
		}
		
		/*
		 * These lines are specific for Niall's dataset. Not necessary for general code.
		 *
	
		//image.setRoi(1200, 750, 3600, 2500); // cropping image to center of image and halving the size.
		//image = image.crop();
		NoduleSegmentation.image = image;
		ColorClustering cluster = new ColorClustering(image);
		cluster.loadClusterer(modelFile.getAbsolutePath());
		cluster.setChannels(channels);
	
		
	try {
			NoduleData noduledata = new NoduleData(cluster,thisType, thisSpecies);
			noduledata.run();
		
	}	catch(Exception e) {
		e.printStackTrace();
	}
	
    	}
    	}
    	}
    	
    	*/
    	
    	File file = null;
		
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Image to load or file to iterate through.");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

        // Add a file filter for image files (you can customize this for specific image types)
        FileNameExtensionFilter imageFilter = new FileNameExtensionFilter("Image Files", "jpg", "jpeg", "png", "gif");
        fileChooser.setFileFilter(imageFilter);
        
        int result = fileChooser.showOpenDialog(null);
        
        if( result == JFileChooser.APPROVE_OPTION) {
        	file = fileChooser.getSelectedFile();
        }
        
       if(file == null) {
    	   System.out.println("Sorry, but the file you selected is not valid.");
    	   return;
       }
    	
    	int FILETYPE = 0;
    	
    	
    	FILETYPE = getFileType(modelFile);
    	if( FILETYPE != MODEL) {
    		IJ.log("Please select a .model file when prompted. If you do not have one, you can "
    				+"generate one using Weka's ColorClustering plugin via ImageJ (or FIJI) to make one. "
    				+ "Instructions can be foudn on this plugin's github page.");
    		System.exit(0);
    	}
    	
    	FILETYPE = getFileType(file);
    	
    	int subtype = 0;
    	
    	switch(FILETYPE) {
    	case FOLDER: 
    		for(File subfile : file.listFiles()) {
    			subtype = getFileType(subfile);
    			
    			if(subtype != IMAGE) {
        			continue;
        		}
    			try {
    				ImagePlus tempim = new ImagePlus(subfile.getPath());
    				execute(tempim, modelFile.getPath());
    			}catch(Exception e) {
    				e.printStackTrace();
    				System.out.println("Could not generate data for " + subfile.getName());
    				continue;
    			}
    			
    		}
    		break;
    		
    	case IMAGE:
    		try {
    		execute(new ImagePlus(file.getPath()), modelFile.getPath());
    		}catch(Exception e) {
    			e.printStackTrace();
    			System.out.println("Could not generate data for " + file.getName());
    		}
    		break;
    		
    	case OTHERFILETYPE:
    		IJ.log("no acceptable filetype found.");
    		System.exit(0);
    		break;
    	}
    	
    
    	IJ.log("done");
    }//===========================================================================================

    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.launch(args);
            // invoke the plugin
            ij.command().run(NoduleSegmentation.class, true);
        }
    }


