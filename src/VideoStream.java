//VideoStream

import java.io.*;

public class VideoStream {

    //FileInputStream fis; //video file
    RandomAccessFile fis;
    int frame_nb; //current frame nb

    //-----------------------------------
    //constructor
    //-----------------------------------
    public VideoStream(String filename, int imagenb) throws Exception{

        //init variables
        fis = new RandomAccessFile(filename,"r");//new FileInputStream(filename);
        frame_nb = imagenb;
        
    }

    
    public int getframe(int ind, byte[] frame) throws Exception
    {
        int length = 0;
        String length_string;
        byte[] frame_length = new byte[5];
        int tmp = -1;
        if (frame_nb < ind){
	        for(int i=frame_nb;i<ind;i++){
		        //read current frame length
		        fis.read(frame_length,0,5);
		
		        //transform frame_length to integer
		        length_string = new String(frame_length);
		        length = Integer.parseInt(length_string);
		
		        tmp = fis.read(frame,0,length);
	        }
	        frame_nb = ind;
        }
        else{
	        fis.read(frame_length,0,5);
	    	
	        //transform frame_length to integer
	        length_string = new String(frame_length);
	        length = Integer.parseInt(length_string);
	
	       // fis.seek(length * (ind-1));
	        tmp = fis.read(frame,0,length);
        }
        return tmp;
    }
    //-----------------------------------
    // getnextframe
    //returns the next frame as an array of byte and the size of the frame
    //-----------------------------------
    public int getnextframe(byte[] frame) throws Exception
    {
        int length = 0;
        String length_string;
        byte[] frame_length = new byte[5];

        //read current frame length
        fis.read(frame_length,0,5);

        //transform frame_length to integer
        length_string = new String(frame_length);
        length = Integer.parseInt(length_string);

        return(fis.read(frame,0,length));
    }
}