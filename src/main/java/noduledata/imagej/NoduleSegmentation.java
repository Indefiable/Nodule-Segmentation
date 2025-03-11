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
 * This is the parent class or 'main' class for this project. It starts the project 
 * and initializes the Menu object.
 * 
 * @author Brandin Farris
 *
 */
@Plugin(type = Command.class, menuPath = "Plugins>Nodule Segmentation")
public class NoduleSegmentation implements Command {
	
	
	private final int SCALEFACTOR = 2;
	public static ImagePlus image;
	
    @Parameter
    private LogService logService;

    @Parameter
    private UIService uiService;

    @Parameter
    private OpService opService;
    
    
    /**
     * This method executes the image analysis for a single image then saves the output.
     *
     * @param image : image to run the data analysis on.
     * @param model : path file to selected .model file.
     * @param saveFile : the user-designated path to save output.
     * @param menu : menu object that stores users input.
     */
    private void execute(ImagePlus image, String model, String saveFile, Menu menu) {
    	
    	ArrayList<Channel> channels = new ArrayList<Channel>(); // channels to use when segmenting.
    	channels.add(Channel.Red);
    	channels.add(Channel.Green);
   
		
    	if(image.getType() != ImagePlus.COLOR_RGB) {
			image = new ImagePlus(image.getTitle(), image.getProcessor().convertToRGB());
		}
	
		NoduleSegmentation.image= image;
		ColorClustering cluster = new ColorClustering(image);
		cluster.loadClusterer(model);
		cluster.setChannels(channels);
	
	try {
			NoduleData noduledata = new NoduleData(cluster,menu.redSingle, menu.greenSingle, menu.mixedSingle);
			noduledata.run(saveFile);
			
			noduledata = null;
			System.gc();
	}	catch(Exception e) {
		e.printStackTrace();
	}
    }
     
    /**
     * crops the image to the center of itself, removing an amount 
     * based on the global SCALEFACTOR
     * TODO: change scalefactor to be a user-based input with the option to crop... is this
     * useful? 
     * 
     * @param imp : image to crop.
     * @return : cropped image.
     */
    private ImagePlus crop(ImagePlus imp) {
    	
		int newWidth = (int) (imp.getWidth() / SCALEFACTOR);
		int newHeight = (int) (imp.getHeight() / SCALEFACTOR);
		
		int x =(int) ((imp.getWidth() - newWidth)/2);
		
		int y =(int) ((imp.getHeight() - newHeight)/2);
		
		imp.setRoi(x, y, newWidth, newHeight); // cropping image to center of image and halving the size.
		imp = imp.crop();
		imp.setTitle(imp.getTitle().substring(4));
		
		return imp;
    }

    @Override
    public void run() {
    	Menu menu = new Menu();
    	menu.run();
    	
    	if(menu.file == null || menu.modelFile == null || menu.saveFile == null) {
    		IJ.log("done");
    		return;
    	}
    	File file = menu.file;
    	File modelFile = menu.modelFile;
    	File saveFile = menu.saveFile;
    	
    	int FILETYPE = Menu.getFileType(file);
    	
    	int subtype = 0;
    	
    	switch(FILETYPE) {
    	case Menu.FOLDER:
    		for(File subfile : file.listFiles()) {
    			subtype = Menu.getFileType(subfile);
    			
    			if(subtype != Menu.IMAGE) {
        			continue;
        		}
    			try {
    				ImagePlus tempim = new ImagePlus(subfile.getPath());
    				execute(tempim, modelFile.getPath(), saveFile.getAbsolutePath(), menu);
    			}catch(Exception e) {
    				
    				System.out.println("++++++++++++++++++++++++++");
    				System.out.println("Could not generate data for " + subfile.getName());
    				System.out.println("++++++++++++++++++++++++++");
    				e.printStackTrace();
    				continue;
    			}
    		}
    		break;
    		
    	case Menu.IMAGE:
    		try {
    			ImagePlus imp = new ImagePlus(file.getPath());
    			
    		execute(imp, modelFile.getPath(), saveFile.getAbsolutePath(), menu);
    		}catch(Exception e) {
    			System.out.println("++++++++++++++++++++++++++");
    			System.out.println("Could not generate data for " + file.getName());
    			System.out.println("++++++++++++++++++++++++++");
    			e.printStackTrace();
    		}
    		break;
    		
    	case Menu.OTHERFILETYPE:
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
        final ImageJ ij = new ImageJ();
        ij.launch(args);
            ij.command().run(NoduleSegmentation.class, true);
        }
    }


