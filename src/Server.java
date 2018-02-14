/* ------------------
   Server
   usage: java Server [RTSP listening port]
   ---------------------- */


import java.io.*;
import java.net.*;
import java.awt.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.Timer;

import java.awt.image.*;

import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;


//import Server.redirectButtonListener;

class RTSPConnection implements Runnable{
	Server server;
	RTSPConnection(Server server){
		this.server = server;
	}
	public void run(){

		try{

        //Get Client IP address
        server.ClientIPAddr = server.RTSPsocket.getInetAddress();
        try {
        	server.logFile.write("client IP addr: "+server.ClientIPAddr);
        	server.logFile.write("Client Port: "+ server.RTSPsocket.getPort() + "\n");
        	server.logFile.flush();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        //Initiate RTSPstate
        server.state = Server.INIT;

        //Set input and output stream filters:
        server.RTSPBufferedReader = new BufferedReader(new InputStreamReader(server.RTSPsocket.getInputStream()) );
        server.RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(server.RTSPsocket.getOutputStream()) );
        
        //Wait for the SETUP message from the client
        int request_type;
        boolean done = false;
        while(!done) {
            request_type = server.parseRequest(); //blocking
    
            if (request_type == Server.SETUP) {
                done = true;

                //update RTSP state
                server.state = Server.READY;
                try {
                	server.logFile.write("New RTSP state: READY\n");
                	server.logFile.flush();
        		} catch (IOException e1) {
        			// TODO Auto-generated catch block
        			e1.printStackTrace();
        		}
             
                //Send response
                server.sendSetup();
             
                //init the VideoStream object:
                server.video = new VideoStream(server.VideoFileName,server.imagenb);

                try {
                	server.logFile.write("Server RTP port: "+ server.RTP_Server_PORT+" RTCP server port: "+server.RTCP_Server_PORT + "\n");
                	server.logFile.flush();
        		} catch (IOException e1) {
        			// TODO Auto-generated catch block
        			e1.printStackTrace();
        		}
                //init RTP and RTCP sockets
                server.RTPsocket = new DatagramSocket(server.RTP_Server_PORT);
                server.RTCPsocket = new DatagramSocket(server.RTCP_Server_PORT);
            }
            else if (request_type == Server.OPTIONS) {
            	//done = true;
            	 try {
                 	server.logFile.write("Received OPTIONS request");
                 	server.logFile.flush();
         		} catch (IOException e1) {
         			// TODO Auto-generated catch block
         			e1.printStackTrace();
         		}
                server.sendOptions();
              //init the VideoStream object:
                server.video = new VideoStream(server.VideoFileName,server.imagenb);
            }
            else if (request_type == Server.DESCRIBE) {
            	//done= true;
            	try {
                 	server.logFile.write("Received DESCRIBE request\n");
                 	server.logFile.flush();
         		} catch (IOException e1) {
         			// TODO Auto-generated catch block
         			e1.printStackTrace();
         		}
                server.sendDescribe();
            }
            
        }

        //loop to handle RTSP requests
        while(true) {
            //parse the request
            request_type = server.parseRequest(); //blocking
            if (request_type == Server.SETUP) {
            	try {
                 	server.logFile.write("SETUP RECEIVED.");
                 	server.logFile.flush();
         		} catch (IOException e1) {
         			// TODO Auto-generated catch block
         			e1.printStackTrace();
         		}
                done = true;

                //update RTSP state
                server.state = Server.READY;
                try {
                 	server.logFile.write("New RTSP state: READY\n");
                 	server.logFile.flush();
         		} catch (IOException e1) {
         			// TODO Auto-generated catch block
         			e1.printStackTrace();
         		}
                //Send response
                server.sendResponse();
             
                //init the VideoStream object:
                server.video = new VideoStream(server.VideoFileName,server.imagenb);

                //init RTP and RTCP sockets
                server.RTPsocket = new DatagramSocket(server.RTP_Server_PORT);
                server.RTCPsocket = new DatagramSocket(server.RTCP_Server_PORT);
            }
            else if ((request_type == server.PLAY) && (server.state == Server.READY)) {
                //send back response
                server.sendResponse();
                //start timer
                server.timer.start();
                server.rtcpReceiver.startRcv();
                //update state
                server.state = Server.PLAYING;
                try {
                 	server.logFile.write("New RTSP state: PLAYING\n");
                 	server.logFile.flush();
         		} catch (IOException e1) {
         			// TODO Auto-generated catch block
         			e1.printStackTrace();
         		}
            }
            else if ((request_type == Server.PAUSE) && (server.state == Server.PLAYING)) {
                //send back response
                server.sendResponse();
                //stop timer
                server.timer.stop();
                server.rtcpReceiver.stopRcv();
                //update state
                server.state = Server.READY;
                try {
                 	server.logFile.write("New RTSP state: READY\n");
                 	server.logFile.flush();
         		} catch (IOException e1) {
         			// TODO Auto-generated catch block
         			e1.printStackTrace();
         		}
            }
            else if (request_type == Server.TEARDOWN) {
                //send back response
                server.sendResponse();
                //stop timer
                server.timer.stop();
                server.rtcpReceiver.stopRcv();
                //close sockets
                //server.RTCPsocket.close();
                server.RTSPsocket.close();
                server.RTPsocket.close();
                //break;
                System.exit(0);
            }
            else if (request_type == Server.DESCRIBE) {
            	try {
                 	server.logFile.write("Received DESCRIBE request\n");
                 	server.logFile.flush();
         		} catch (IOException e1) {
         			// TODO Auto-generated catch block
         			e1.printStackTrace();
         		}
                server.sendDescribe();
                break;
            }
            else if (request_type == Server.OPTIONS) {
            	try {
                 	server.logFile.write("Received OPTIONS request\n");
                 	server.logFile.flush();
         		} catch (IOException e1) {
         			// TODO Auto-generated catch block
         			e1.printStackTrace();
         		}
                server.sendOptions();
            }
            else if (request_type == Server.GET_PARAMETER) {
            	try {
                 	server.logFile.write("Received GET_PARAMETER request\n");
                 	server.logFile.flush();
         		} catch (IOException e1) {
         			// TODO Auto-generated catch block
         			e1.printStackTrace();
         		}
            	server.sendResponse();
            }
        }

    }
		catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
}
      


//public class Server extends JFrame implements ActionListener {
public class Server implements ActionListener {	
	static Vector<RTSPConnection> all_connections = new Vector<RTSPConnection>();
	//General Thread variables
	int index;
	String LogFile;
	BufferedWriter logFile;
    //RTP variables:
    //----------------
    DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets
    DatagramPacket senddp; //UDP packet containing the video frames

    InetAddress ClientIPAddr;   //Client IP address
    int RTP_Client_port = 0;      //destination port for RTP packets  (given by the RTSP Client)
    /*static*/ int RTP_Server_PORT = 19000; //port where the client will receive the RTP packets
   // int RTP_dest_port_v = 0;	//destination port for RTP packets voice  (given by the RTSP Client)
    
    InetAddress ServerIPAddr;	//Server IP address
    int Server_Port = 0;	//Server Port

    //Video variables:
    //----------------
    int imagenb = 0; //image nb of the image currently transmitted
    VideoStream video; //VideoStream object used to access video frames
    /*static*/ int MJPEG_TYPE = 26; //RTP payload type for MJPEG video
    /*static*/ int FRAME_PERIOD = 25;//50; //Frame period of the video to stream, in ms
    /*static*/ int VIDEO_LENGTH = 1200; //length of the video in frames

    Timer timer;    //timer used to send the images at the video frame rate
    byte[] buf;     //buffer used to store the images to send to the client 
    int sendDelay;  //the delay to send images over the wire. Ideally should be
                    //equal to the frame rate of the video file, but may be 
                    //adjusted when congestion is detected.

    //RTSP variables
    //----------------
    //rtsp states
    final static int INIT = 0;
    final static int READY = 1;
    final static int PLAYING = 2;
    //rtsp message types
    final static int SETUP = 3;
    final static int PLAY = 4;
    final static int PAUSE = 5;
    final static int TEARDOWN = 6;
    final static int DESCRIBE = 7;
    final static int OPTIONS = 8;
    final static int GET_PARAMETER = 9;

    /*static */int state; //RTSP Server state == INIT or READY or PLAY
    Socket RTSPsocket; //socket used to send/receive RTSP messages
    //input and output stream filters
    /*static*/ BufferedReader RTSPBufferedReader;
    /*static*/ BufferedWriter RTSPBufferedWriter;
    /*static*/ String VideoFileName; //video file requested from the client
    /*static*/ String RTSPid = UUID.randomUUID().toString(); //ID of the RTSP session
    int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session
    int RTSP_Server_port = 0;


    //RTCP variables
    //----------------
    /*static*/ int RTCP_Server_PORT = 19001; //port where the client will receive the RTP packets
    int RTCP_Client_port = 0;
    /*static*/ int RTCP_PERIOD = 400;     //How often to check for control events
    DatagramSocket RTCPsocket;
    RtcpReceiver rtcpReceiver;
    int congestionLevel;

    //Performance optimization and Congestion control
    ImageTranslator imgTranslator;
    CongestionController cc;
    
    final static String CRLF = "\r\n";

    //--------------------------------
    //Constructor
    //--------------------------------
    public Server( int _index, int portNum) throws IOException {

    	this.RTSP_Server_port = portNum;
    	this.index = _index;
    	this.RTP_Server_PORT += this.index * 2;
    	this.RTCP_Server_PORT = this.RTP_Server_PORT + 1;
    	LogFile= "Log_" + Integer.toString(this.index) + "_"+ Integer.toString(this.RTSP_Server_port) + ".log";
        logFile = new BufferedWriter(new FileWriter(LogFile));

        logFile.flush();
        //init RTP sending Timer
        sendDelay = FRAME_PERIOD;
        timer = new Timer(sendDelay, this);
        timer.setInitialDelay(0);
        timer.setCoalesce(true);

        //init congestion controller
        cc = new CongestionController(600);

        //allocate memory for the sending buffer
        buf = new byte[20000]; 

        //init the RTCP packet receiver
        rtcpReceiver = new RtcpReceiver(RTCP_PERIOD);
        imgTranslator = new ImageTranslator(0.8f);
    }
    
                
    
    //------------------------
    //Handler for timer
    //------------------------
    public void actionPerformed(ActionEvent e) {
        byte[] frame;

        //if the current image nb is less than the length of the video
        if (imagenb < VIDEO_LENGTH) {
            //update current imagenb
        	try {
				logFile.write("###### Image number:" +imagenb + "\n");
				logFile.flush();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
            imagenb++;

            try {
                //get next frame to send from the video, as well as its size
                int image_length = video.getframe(imagenb,buf);

                //adjust quality of the image if there is congestion detected
                if (congestionLevel > 0) {
                    imgTranslator.setCompressionQuality(1.0f - congestionLevel * 0.2f);
                    frame = imgTranslator.compress(Arrays.copyOfRange(buf, 0, image_length));
                    image_length = frame.length;
                    System.arraycopy(frame, 0, buf, 0, image_length);
                }

                //Builds an RTPpacket object containing the frame
                long timestamp = System.currentTimeMillis();
                //RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb, imagenb*FRAME_PERIOD, buf, image_length);
                RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb, imagenb*FRAME_PERIOD, buf, image_length, logFile);
                
                //get to total length of the full rtp packet to send
                int packet_length = rtp_packet.getlength();

                //retrieve the packet bitstream and store it in an array of bytes
                byte[] packet_bits = new byte[packet_length];
                rtp_packet.getpacket(packet_bits);

                //send the packet as a DatagramPacket over the UDP socket 
                senddp = new DatagramPacket(packet_bits, packet_length,ClientIPAddr, RTP_Client_port); //NEDA CHANGE THIS SOONN: ClientIPAddr
                RTPsocket.send(senddp);

                try {
                	logFile.write("Send frame #" + imagenb + ", Frame size: " + image_length + " (" + buf.length + ")\n");
                	logFile.flush();
    			} catch (IOException e1) {
    				// TODO Auto-generated catch block
    				e1.printStackTrace();
    			}
                
                //print the header bitstream
                rtp_packet.printheader();

                //update GUI
                try {
                	logFile.write("Send frame #" + imagenb);
                	logFile.flush();
    			} catch (IOException e1) {
    				// TODO Auto-generated catch block
    				e1.printStackTrace();
    			}
            }
            catch(Exception ex) {
                System.out.println("Exception caught: "+ex);
                System.exit(0);
            }
        }
        else {
        	 try {
             	logFile.write("All frames are sent\n");
             	logFile.flush();
 			} catch (IOException e1) {
 				// TODO Auto-generated catch block
 				e1.printStackTrace();
 			}
        }
    }

    //------------------------
    //Controls RTP sending rate based on traffic
    //------------------------
    class CongestionController implements ActionListener {
        private Timer ccTimer;
        int interval;   //interval to check traffic stats
        int prevLevel;  //previously sampled congestion level

        public CongestionController(int interval) {
            this.interval = interval;
            ccTimer = new Timer(interval, this);
            ccTimer.start();
        }

        public void actionPerformed(ActionEvent e) {

            //adjust the send rate
            if (prevLevel != congestionLevel) {
                sendDelay = FRAME_PERIOD + congestionLevel * (int)(FRAME_PERIOD * 0.1);
                timer.setDelay(sendDelay);
                prevLevel = congestionLevel;
                try{
                	logFile.write("Send delay changed to: " + sendDelay + "\n");
                	logFile.flush();
                }catch (IOException e1) {
    				// TODO Auto-generated catch block
    				e1.printStackTrace();
    			}
                //System.out.println("Send delay changed to: " + sendDelay);
            }
        }
    }

    //------------------------
    //Listener for RTCP packets sent from client
    //------------------------
    class RtcpReceiver implements ActionListener {
        private Timer rtcpTimer;
        private byte[] rtcpBuf;
        int interval;

        public RtcpReceiver(int interval) {
            //set timer with interval for receiving packets
            this.interval = interval;
            rtcpTimer = new Timer(interval, this);
            rtcpTimer.setInitialDelay(0);
            rtcpTimer.setCoalesce(true);

            //allocate buffer for receiving RTCP packets
            rtcpBuf = new byte[512];
        }

        public void actionPerformed(ActionEvent e) {
            

            try {
            	//Construct a DatagramPacket to receive data from the UDP socket
                DatagramPacket dp = new DatagramPacket(rtcpBuf, rtcpBuf.length);// you are receiving a packet so no need to IP and port info => have omitted this: ,ClientIPAddr, RTCP_Client_port);
                float fractionLost;
                RTCPsocket.receive(dp);   // Blocking
                RTCPpacket rtcpPkt = new RTCPpacket(dp.getData(), dp.getLength());
                //System.out.println("[RTCP] " + rtcpPkt);

                try {
                	logFile.write("[RTCP] " + rtcpPkt + "\n");
                	logFile.flush();
    			} catch (IOException e1) {
    				// TODO Auto-generated catch block
    				e1.printStackTrace();
    			}
                //set congestion level between 0 to 4
                fractionLost = rtcpPkt.fractionLost;
                if (fractionLost >= 0 && fractionLost <= 0.01) {
                    congestionLevel = 0;    //less than 0.01 assume negligible
                }
                else if (fractionLost > 0.01 && fractionLost <= 0.25) {
                    congestionLevel = 1;
                }
                else if (fractionLost > 0.25 && fractionLost <= 0.5) {
                    congestionLevel = 2;
                }
                else if (fractionLost > 0.5 && fractionLost <= 0.75) {
                    congestionLevel = 3;
                }
                else {
                    congestionLevel = 4;
                }
            }
            catch (InterruptedIOException iioe) {
                System.out.println("Nothing to read");
            }
            catch (IOException ioe) {
                System.out.println("Exception caught: "+ioe);
            }
        }

        public void startRcv() {
            rtcpTimer.start();
        }

        public void stopRcv() {
            rtcpTimer.stop();
        }
    }

    //------------------------------------
    //Translate an image to different encoding or quality
    //------------------------------------
    class ImageTranslator {

        private float compressionQuality;
        private ByteArrayOutputStream baos;
        private BufferedImage image;
        private Iterator<ImageWriter>writers;
        private ImageWriter writer;
        private ImageWriteParam param;
        private ImageOutputStream ios;

        public ImageTranslator(float cq) {
            compressionQuality = cq;

            try {
                baos =  new ByteArrayOutputStream();
                ios = ImageIO.createImageOutputStream(baos);

                writers = ImageIO.getImageWritersByFormatName("jpeg");
                writer = (ImageWriter)writers.next();
                writer.setOutput(ios);

                param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(compressionQuality);

            } catch (Exception ex) {
                System.out.println("Exception caught: "+ex);
                System.exit(0);
            }
        }

        public byte[] compress(byte[] imageBytes) {
            try {
                baos.reset();
                image = ImageIO.read(new ByteArrayInputStream(imageBytes));
                writer.write(null, new IIOImage(image, null, null), param);
            } catch (Exception ex) {
                System.out.println("Exception caught: "+ex);
                System.exit(0);
            }
            return baos.toByteArray();
        }

        public void setCompressionQuality(float cq) {
            compressionQuality = cq;
            param.setCompressionQuality(compressionQuality);
        }
    }

    //------------------------------------
    //Parse RTSP Request
    //------------------------------------
    public int parseRequest() {
        int request_type = -1;
        try { 
        	if (!RTSPBufferedReader.ready())
        		return request_type;
            //parse request line and extract the request_type:
            String RequestLine = RTSPBufferedReader.readLine();
            if(RequestLine.compareTo("") != 0){
            	try {
                	logFile.write("RTSP Server - Received from Client:");
                	logFile.write(RequestLine + "\n");
                	logFile.flush();
    			} catch (IOException e1) {
    				// TODO Auto-generated catch block
    				e1.printStackTrace();
    			}
            }
            else
            	return request_type;

            StringTokenizer tokens = new StringTokenizer(RequestLine," :/");
            String request_type_string = tokens.nextToken();
            

            //convert to request_type structure:
            if ((new String(request_type_string)).compareTo("SETUP") == 0)
                request_type = SETUP;
            else if ((new String(request_type_string)).compareTo("PLAY") == 0)
                request_type = PLAY;
            else if ((new String(request_type_string)).compareTo("PAUSE") == 0)
                request_type = PAUSE;
            else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0)
                request_type = TEARDOWN;
            else if ((new String(request_type_string)).compareTo("DESCRIBE") == 0)
                request_type = DESCRIBE;
            else if ((new String(request_type_string)).compareTo("OPTIONS") == 0)
            	request_type = OPTIONS;
            else if ((new String(request_type_string)).compareTo("GET_PARAMETER")==0)
            	request_type = GET_PARAMETER;
            
            //parse the SeqNumLine and extract CSeq field
            String SeqNumLine = RTSPBufferedReader.readLine();
            try {
            	logFile.write(RequestLine + "\n");
            	logFile.flush();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
            tokens = new StringTokenizer(SeqNumLine);
            tokens.nextToken();
            RTSPSeqNb = Integer.parseInt(tokens.nextToken());
        
            //get User-Agent Line
            String LastLine = RTSPBufferedReader.readLine();
            try {
            	logFile.write("User-Agent line: " +LastLine + "\n");
            	logFile.flush();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
            if (request_type == OPTIONS){
            	tokens = new StringTokenizer(RequestLine," ://");
            	tokens.nextToken(); // skip Request Type
            	String token = tokens.nextToken(); //skip rtsp
            	ServerIPAddr = InetAddress.getByName(tokens.nextToken());
            	Server_Port = Integer.parseInt(tokens.nextToken());
            	while (token!= null && !token.contains("Mjpeg") && !token.contains("vlc")){
            		token = tokens.nextToken();
            	}
            	try {
                	logFile.write("****Video file name: "+ token + "Server IP address: "+ ServerIPAddr + "\n");
                	logFile.flush();
    			} catch (IOException e1) {
    				// TODO Auto-generated catch block
    				e1.printStackTrace();
    			}
                VideoFileName = token;
            }
            else if (request_type == SETUP ) {
                //extract RTP_dest_port from LastLine
            	String transport = RTSPBufferedReader.readLine();
            	//System.out.println("Setup transport line: "+ transport);
            	try {
                	logFile.write("Setup transport line: "+ transport + "\n");
                	logFile.flush();
    			} catch (IOException e1) {
    				// TODO Auto-generated catch block
    				e1.printStackTrace();
    			}
            	tokens = new StringTokenizer(transport, ";=");
                String token = tokens.nextToken();
            	while (token.compareTo("client_port") != 0){
            		token = tokens.nextToken();
            	}
            	token = tokens.nextToken();
            	StringTokenizer ports = new StringTokenizer(token,"-");
            	
                RTP_Client_port = Integer.parseInt(ports.nextToken());
                RTCP_Client_port = Integer.parseInt(ports.nextToken());
                //RTCP_RCV_PORT = Integer.parseInt(ports.nextToken());
                try {
                	logFile.write("SETUP Next Line: "+ RTSPBufferedReader.readLine() + "\n");
                	logFile.flush();
    			} catch (IOException e1) {
    				// TODO Auto-generated catch block
    				e1.printStackTrace();
    			}
                //System.out.println("SETUP Next Line: "+ RTSPBufferedReader.readLine());
            }
            else if (request_type == DESCRIBE) {
                //tokens.nextToken();
                //String describeDataType = tokens.nextToken();
            	try {
                	logFile.write("Next Line: "+ RTSPBufferedReader.readLine() + "\n");
                	logFile.flush();
    			} catch (IOException e1) {
    				// TODO Auto-generated catch block
    				e1.printStackTrace();
    			}
            }
            else if (request_type == PLAY){ // check for range
            	String sessionLine = RTSPBufferedReader.readLine();
            	String rangeLine = RTSPBufferedReader.readLine();
            	tokens = new StringTokenizer(rangeLine,"=-:");
                tokens.nextToken(); //skip Range:
                tokens.nextToken(); //skip npt
                //RTSPid = tokens.nextToken();
                //otherwise LastLine will be the SessionId line
                imagenb =  (int)(Double.parseDouble(tokens.nextToken()));
                try {
                	logFile.write("Image number: "+imagenb);
                	logFile.write("PLAY Next Line: "+ RTSPBufferedReader.readLine() + "\n");
                	logFile.flush();
    			} catch (IOException e1) {
    				// TODO Auto-generated catch block
    				e1.printStackTrace();
    			}
            }
            else if (request_type == TEARDOWN){
            	String sessionLine = RTSPBufferedReader.readLine();
            	//System.out.println("TearDown Next Line: "+sessionLine +  RTSPBufferedReader.readLine());
            	try {
                	logFile.write("TearDown Next Line: "+sessionLine +  RTSPBufferedReader.readLine() + "\n");
                	logFile.flush();
    			} catch (IOException e1) {
    				// TODO Auto-generated catch block
    				e1.printStackTrace();
    			}
            }
            else if (request_type == PAUSE){
            	String sessionLine = RTSPBufferedReader.readLine();
            	try {
                	logFile.write("TearDown Next Line: "+ sessionLine + RTSPBufferedReader.readLine() + "\n");
                	logFile.flush();
    			} catch (IOException e1) {
    				// TODO Auto-generated catch block
    				e1.printStackTrace();
    			}
            }
        } catch(Exception ex) {
            System.out.println("Exception caught: "+ex);
            System.exit(0);
        }
      
        return(request_type);
    }

    // Creates a DESCRIBE response string in SDP format for current media
    private String describe() {
        StringWriter writer1 = new StringWriter();
        StringWriter writer2 = new StringWriter();
        
        // Write the body first so we can get the size later
        writer2.write("v=0" + CRLF);
        writer2.write("o=- 0 0 IN IP4 "+ClientIPAddr.toString().split("/")[1] + CRLF);
        writer2.write("s="+RTSPid + CRLF);
        writer2.write("i=rtsp-server" + CRLF);
        writer2.write("e=NONE" + CRLF);
        writer2.write("t=0 0" + CRLF);
        writer2.write("a=tool:GStreamer" + CRLF);
        writer2.write("a=type:broadcast" + CRLF);
        writer2.write("a=control:*" + CRLF);
        writer2.write("a=range:npt=0.000000-119.961667" + CRLF);
        writer2.write("m=video 0"+ " RTP/AVP "+ MJPEG_TYPE + CRLF);
        writer2.write("c=IN IP4 "+ClientIPAddr.toString().split("/")[1] + CRLF);
        writer2.write("a=rtpmap:"+MJPEG_TYPE+" JPEG/90000" + CRLF);
        writer2.write("a=control:stream=0");
        //writer2.write("a=mimetype:string;\"video/MJPEG\"" + CRLF);
        writer2.write(CRLF);
        String body = writer2.toString();
    
        final Date currentTime = new Date();
        final SimpleDateFormat sdf =
        new SimpleDateFormat("EEE, d MMM yyyy hh:mm:ss z");

        // 	Give it to me in GMT time.
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        //System.out.println("GMT time: " + sdf.format(currentTime));
        
        
        writer1.write("Content-Type: " + "application/sdp" + CRLF);
        writer1.write("Content-Base: rtsp://127.0.0.1:1051/" + VideoFileName + CRLF);
        writer1.write("Server: GStreamer RTSP server" + CRLF);
        writer1.write("Date: "+sdf.format(currentTime)+CRLF);//Thr, 10 Aug 2017 13:58:53 GMT"
        writer1.write("Content-Length: " + body.length() + CRLF);
        writer1.write(CRLF);// + CRLF);
        writer1.write(body);
        //writer1.write(CRLF);
        try {
        	logFile.write("DESCRIBE RESPONSE: "+writer1.toString() + "\n");
        	logFile.flush();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        //System.out.println("DESCRIBE RESPONSE: "+writer1.toString());
        
        return writer1.toString();
    }

    //------------------------------------
    //Send RTSP Response
    //------------------------------------
    public void sendResponse() {
        try {
            RTSPBufferedWriter.write("RTSP/1.0 200 OK"+CRLF);
            RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);
            //RTSPBufferedWriter.write("Session: "+RTSPid+CRLF);
            RTSPBufferedWriter.write(CRLF);
            RTSPBufferedWriter.flush();
            try {
            	logFile.write("RTSP Server - Sent response to Client.\n");
            	logFile.flush();
    		} catch (IOException e1) {
    			// TODO Auto-generated catch block
    			e1.printStackTrace();
    		}
            //System.out.println("RTSP Server - Sent response to Client.");
        } catch(Exception ex) {
            System.out.println("Exception caught: "+ex);
            System.exit(0);
        }
    }

    public void sendSetup(){
    	try{
    		final Date currentTime = new Date();
    		final SimpleDateFormat sdf =
	        new SimpleDateFormat("EEE, d MMM yyyy hh:mm:ss z");

	        // 	Give it to me in GMT time.
	        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
	  
    		
    		RTSPBufferedWriter.write("RTSP/1.0 200 OK"+CRLF);
    		RTSPBufferedWriter.write("Transport: RTP/AVP;unicast;client_port="+RTP_Client_port+"-"+RTCP_Client_port+
    				";server_port="+RTP_Server_PORT+"-"+RTCP_Server_PORT+";mode=play;source="
    		+ServerIPAddr.toString().split("/")[1]+";ssrc=1337"+CRLF);//+RTSPSeqNb
    		RTSPBufferedWriter.write("Server: GStreamer RTSP server" + CRLF);
    		//RTSPBufferedWriter.write("Content-Length: 0" + CRLF);
    		//RTSPBufferedWriter.write("Supported: play.basic, con.persistent" + CRLF);
    		RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);
    		RTSPBufferedWriter.write("RTP-Info: url=rtsp://"+ServerIPAddr.toString().split("/")[1]+":"+Server_Port
    				+"/"+VideoFileName+";seq=0;rtptime=0"+CRLF);
    		RTSPBufferedWriter.write("Date: "+sdf.format(currentTime)+CRLF);//Thr, 10 Aug 2017 13:58:53 GMT"
    		//RTSPBufferedWriter.write("Cache-Control: no-cache"+CRLF);
            RTSPBufferedWriter.write("Session: "+RTSPid+CRLF);
            //RTSPBufferedWriter.write(CRLF);
            RTSPBufferedWriter.write(CRLF);   
            RTSPBufferedWriter.flush();
    	} catch(Exception ex){
    		System.out.println("Exception caught: "+ex);
            System.exit(0);
    	}
    }
    public void sendDescribe() {
        String des = describe();
        try {
            RTSPBufferedWriter.write("RTSP/1.0 200 OK"+CRLF);
            RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);
            RTSPBufferedWriter.write(des);
            //RTSPBufferedWriter.write(CRLF+CRLF);
            RTSPBufferedWriter.flush();
            try {
            	logFile.write("RTSP Server - Sent response to Client.\n");
            	logFile.flush();
    		} catch (IOException e1) {
    			// TODO Auto-generated catch block
    			e1.printStackTrace();
    		}
            //System.out.println("RTSP Server - Sent response to Client.");
        } catch(Exception ex) {
            System.out.println("Exception caught: "+ex);
            System.exit(0);
        }
    }
    public void sendOptions(){
    	try{
    		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    		Date date = new Date();
    		RTSPBufferedWriter.write("RTSP/1.0 200 OK"+CRLF);
    		RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);//+RTSPSeqNb
    		RTSPBufferedWriter.write("Public: OPTIONS, DESCRIBE, PAUSE, PLAY, SETUP, SET_PARAMETER, TEARDOWN" + CRLF);//, GET_PARAMETER
    		//RTSPBufferedWriter.write("Supported: play.basic, con.persistent" + CRLF);
            RTSPBufferedWriter.write("Server: GStreamer RTSP server"+CRLF);
            RTSPBufferedWriter.write("Date: Mon, 7 Aug 2017 19:10:53 GMT"+CRLF);
            RTSPBufferedWriter.write("Session: "+RTSPid+CRLF);
            //RTSPBufferedWriter.write(CRLF);
            RTSPBufferedWriter.write(CRLF);
            //SHALL I ADD THE REST?
            
            
            RTSPBufferedWriter.flush();
    	} catch(Exception ex){
    		System.out.println("Exception caught: "+ex);
            System.exit(0);
    	}
    }
    
  //------------------------------------
    //main
    //------------------------------------
    public static void main(String argv[]) throws Exception
    {
    	int count = 0;
    	//get RTSP socket port from the command line
        int RTSPport = Integer.parseInt(argv[0]);
        System.out.println(argv[0]);
        //server.RTSP_Server_port = RTSPport;
        ServerCommand comm = new ServerCommand();
        Thread cmd = new Thread(comm);
        cmd.start();
        System.out.println("KHAREJ WhILE");
        ServerSocket listenSocket = new ServerSocket(RTSPport);

    	//create a Server object
        //Initiate TCP connections with the client for the RTSP session
        while(count < 10){            
        	Socket tmpSocket= listenSocket.accept();
        	Server server = new Server(count, RTSPport);
        	server.RTSPsocket = tmpSocket;
        	//server.index = count;
        	count++;
        	//server.RTSP_Server_port = RTSPport;
        	//port numbers
//        	server.RTP_Server_PORT += server.index * 2;
//        	server.RTCP_Server_PORT = server.RTP_Server_PORT + 1;
            //show GUI:
            //server.pack();
            //server.setVisible(true);
            //server.setSize(new Dimension(400, 200));
            //server.setTitle(new String(argv[0]+" "+server.index));

        	RTSPConnection conn = new RTSPConnection(server);
        	Server.all_connections.add(conn);
        	new Thread(conn).start();
        	//listenSocket.close();
        }
        listenSocket.close();
        
    }

}

class ServerCommand implements Runnable{
	ServerCommand(){
		//this.server = server;
		System.out.println("***************************************");
	}
	public void run(){
		try{
			boolean exit = false;
			while(!exit){
				System.out.println("#################################");
				Scanner scan = new Scanner(System.in);
				String cmd = scan.nextLine();
				System.out.println(cmd);
				if ( cmd.matches("-1")){
					exit = true;
					for (int i=0; i < Server.all_connections.size(); i++){
						System.out.println("Server Command"+i);
						Server.all_connections.get(i).server.timer.stop();
						Server.all_connections.get(i).server.rtcpReceiver.stopRcv();
						try {
							Server.all_connections.get(i).server.logFile.close();
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
					}
		        	//timer.stop();
		            //rtcpReceiver.stopRcv();
		            System.exit(0);
				}
				else{
					System.out.println("BEFORE REDIRECT FUNCTION, size of all_connections " + Server.all_connections.size());
					for (int i=0; i < Server.all_connections.size(); i++){
						redirect(Server.all_connections.get(i).server,cmd);
					}
				}
	
	            }
			}
		catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	public void redirect(Server server, String location){
    	//StateRx();
		System.out.println("REDIRECT FUNCTION");
    	try {
        	server.logFile.write("Redirect Button pressed !\n");
        	server.logFile.flush();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        //System.out.println("Redirect Button pressed !");      
        //String des = describe();
        try {
        	server.RTSPBufferedWriter.write("REDIRECT rtsp://" + server.VideoFileName + " RTSP/1.0" + Server.CRLF);
        	server.RTSPBufferedWriter.write("CSeq: "+server.RTSPSeqNb+Server.CRLF);
            //////HERE FOR REDIRECT AFTER NO GUI///////////////////////
            server.RTSPBufferedWriter.write("Location: " + location + Server.CRLF);
            
            //RTSPBufferedWriter.write("Session: "+RTSPid+CRLF);
          //  RTSPBufferedWriter.write(des);
            //RTSPBufferedWriter.write("Server: "+ redirectLable.getText()+CRLF);
        	server.RTSPBufferedWriter.flush();
            try {
            	server.logFile.write("RTSP Server - Sent REDIRECT to Client.\n");
            	server.logFile.flush();
    		} catch (IOException e1) {
    			// TODO Auto-generated catch block
    			e1.printStackTrace();
    		}
            //System.out.println("RTSP Server - Sent REDIRECT to Client.");
        } catch(Exception ex) {
            System.out.println("Exception caught: "+ex);
            System.exit(0);
        } 
    }
}