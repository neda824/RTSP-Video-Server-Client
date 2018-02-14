/* ------------------
   Client
   usage: java Client [Server hostname] [Server RTSP listening port] [Video file requested]
   ---------------------- */

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.security.acl.LastOwnerException;
import java.text.*;
import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.Timer;
import javax.imageio.*;

public class Client {
    BufferedWriter logFile;
	//GUI
    //----
    JFrame f = new JFrame("Client");
    JButton setupButton = new JButton("Setup");
    JButton playButton = new JButton("Play");
    JButton pauseButton = new JButton("Pause");
    JButton tearButton = new JButton("Close");
    JButton describeButton = new JButton("Session");
    
    JPanel mainPanel = new JPanel();
    JPanel buttonPanel = new JPanel();
    JLabel statLabel1 = new JLabel();
    JLabel statLabel2 = new JLabel();
    JLabel statLabel3 = new JLabel();
    JLabel iconLabel = new JLabel();
    ImageIcon icon;

    //RTP variables:
    //----------------
    DatagramPacket rcvdp;            //UDP packet received from the server
    DatagramSocket RTPsocket;        //socket to be used to send and receive UDP packets
    static int RTP_Client_PORT = 25000; //port where the client will receive the RTP packets
    int RTP_Server_PORT;
    
    Timer timer; //timer used to receive data from the UDP socket
    byte[] buf;  //buffer used to store data received from the server 
   
    //RTSP variables
    //----------------
    //rtsp states 
    final static int INIT = 0;
    final static int READY = 1;
    final static int PLAYING = 2;
    static int state;            //RTSP state == INIT or READY or PLAYING
    Socket RTSPsocket;           //socket used to send/receive RTSP messages
    InetAddress ServerIPAddr;
    int RTSP_server_port;
    String RTSP_ADDR;
    String Client_Name = "RTSP Client";

    //input and output stream filters
    static BufferedReader RTSPBufferedReader;
    static BufferedWriter RTSPBufferedWriter;
    static String VideoFileName; //video file to request to the server
    int RTSPSeqNb = 0;           //Sequence number of RTSP messages within the session
    String RTSPid;              // ID of the RTSP session (given by the RTSP Server)

    final static String CRLF = "\r\n";
    final static String DES_FNAME = "session_info.txt";

    //RTCP variables
    //----------------
    DatagramSocket RTCPsocket;          //UDP socket for sending RTCP packets
    static int RTCP_Client_PORT;// set it to RTP_RCV_PORT +1 rather than fixed value = 19001;   //port where the client will receive the RTP packets
    static int RTCP_PERIOD = 400;       //How often to send RTCP packets
    RtcpSender rtcpSender;
    int RTCP_Server_PORT;
    //Video constants:
    //------------------
    static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video

    //Statistics variables:
    //------------------
    double statDataRate;        //Rate of video data received in bytes/s
    int statTotalBytes;         //Total number of bytes received in a session
    double statStartTime;       //Time in milliseconds when start is pressed
    double StartTime;			//Time play is pressed
    double statTotalPlayTime;   //Time in milliseconds of video playing since beginning
    double statLastPktTime;     //Time in milliseconds of last received packet
    float statFractionLost;     //Fraction of RTP data packets from sender lost since the prev packet was sent
    int statCumLost = 0;            //Number of packets lost
    int statExpRtpNb = 0;           //Expected Sequence number of RTP messages within the session
    int statHighSeqNb = 0;          //Highest sequence number received in session
    BufferedWriter statlogFile;
    BufferedWriter plotlogFile;
    

    FrameSynchronizer fsynch;
    playBack playback;
    private final Lock lock= new ReentrantLock();
   
    //--------------------------
    //Constructor
    //--------------------------
    public Client() {

    	 try {
			logFile = new BufferedWriter(new FileWriter("Client_log.log"));
			statlogFile = new BufferedWriter(new FileWriter("Client_stat_log.log"));
			plotlogFile = new BufferedWriter(new FileWriter("Client_plot_log.log"));
			try {
				plotlogFile.write("##,##\n");
				plotlogFile.write("time,IPktTime,CumLoss\n");
				plotlogFile.flush();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        //build GUI
        //--------------------------
     
        //Frame
        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                try {
					plotlogFile.close();
					statlogFile.close();
	                logFile.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
            	System.exit(0);
            }
        });

        //Buttons
        buttonPanel.setLayout(new GridLayout(1,0));
        buttonPanel.add(setupButton);
        buttonPanel.add(playButton);
        buttonPanel.add(pauseButton);
        buttonPanel.add(tearButton);
        buttonPanel.add(describeButton);
        
        
        setupButton.addActionListener(new setupButtonListener());
        playButton.addActionListener(new playButtonListener());
        pauseButton.addActionListener(new pauseButtonListener());
        tearButton.addActionListener(new tearButtonListener());
        describeButton.addActionListener(new describeButtonListener());
        

        //Statistics
        statLabel1.setText("Total Bytes Received: 0");
        statLabel2.setText("Packets Lost: 0");
        statLabel3.setText("Data Rate (bytes/sec): 0");

        //Image display label
        iconLabel.setIcon(null);
        
        //frame layout
        mainPanel.setLayout(null);
        mainPanel.add(iconLabel);
        mainPanel.add(buttonPanel);
        mainPanel.add(statLabel1);
        mainPanel.add(statLabel2);
        mainPanel.add(statLabel3);
        iconLabel.setBounds(0,0,500,280);
        buttonPanel.setBounds(0,280,500,50);
        statLabel1.setBounds(0,330,500,20);
        statLabel2.setBounds(0,350,500,20);
        statLabel3.setBounds(0,370,500,20);

        f.getContentPane().add(mainPanel, BorderLayout.CENTER);
        f.setSize(new Dimension(520,430));
        f.setVisible(true);

        //init timer
        //--------------------------
        timer = new Timer(10, new timerListener());
        timer.setInitialDelay(0);
        timer.setCoalesce(true);

        //init RTCP packet sender
        rtcpSender = new RtcpSender(400);
        playback = new playBack(25);//should be 25

        //allocate enough memory for the buffer used to receive data from the server
        buf = new byte[150000];    

        //create the frame synchronizer
        fsynch = new FrameSynchronizer(200);
    }

    //------------------------------------
    //main
    //------------------------------------
    // 127.0.0.1 1052  movie.Mjpeg 1
    public static void main(String argv[]) throws Exception {
        //Create a Client object
        Client theClient = new Client();
        
        //get server RTSP port and IP address from the command line
        //------------------
        String ServerHost = argv[0];
        int tmp_server_port = Integer.parseInt(argv[1]);
        String video_name = argv[2];
        int index = Integer.parseInt(argv[3]);
        theClient.RTSP_ADDR = "rtsp://"+ServerHost+":"+tmp_server_port+"/"+video_name;
        Client.RTP_Client_PORT += index * 2;
        Client.RTCP_Client_PORT = Client.RTP_Client_PORT + 1;
        System.out.println("Client RTP port: "+ Client.RTP_Client_PORT+" Client RTCP port: "+Client.RTCP_Client_PORT);
        System.out.println("RTSP address: "+theClient.RTSP_ADDR);
        theClient.ServerIPAddr = InetAddress.getByName(ServerHost);
        VideoFileName = video_name;
        theClient.RTSP_server_port = tmp_server_port;
        theClient.RTSPsocket = new Socket(theClient.ServerIPAddr, theClient.RTSP_server_port);

        //Establish a UDP connection with the server to exchange RTCP control packets
        //------------------

        //Set input and output stream filters:
        RTSPBufferedReader = new BufferedReader(new InputStreamReader(theClient.RTSPsocket.getInputStream()));
        RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(theClient.RTSPsocket.getOutputStream()));

        //init RTSP state:
        state = INIT;      
        theClient.StartTime = System.currentTimeMillis();
        
    }
       
    
    private void Setup_Server_Connection(Client theClient, String video_name)throws Exception{
		//theClient.ServerIPAddr = InetAddress.getByName(ServerHost);
        VideoFileName = video_name;
        //theClient.RTSP_server_port = tmp_server_port;
        theClient.RTSPsocket = new Socket(theClient.ServerIPAddr, RTSP_server_port);

        //Establish a UDP connection with the server to exchange RTCP control packets
        //------------------

        //Set input and output stream filters:
        RTSPBufferedReader = new BufferedReader(new InputStreamReader(theClient.RTSPsocket.getInputStream()));
        RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(theClient.RTSPsocket.getOutputStream()));

        //init RTSP state:
        //state = INIT;

    }
    
    public void setupConnection(){
	    if (state == INIT) {
	    	System.out.println("FIRST SETUP IS IN INIT CONDITION");
	
	        //init RTSP sequence number
	        RTSPSeqNb = 1;
	

	        //Send OPTIONS message to the server
	        sendRequest("OPTIONS");
	        lock.lock();
	        if (parseServerResponse("OPTIONS") != 200)
	            System.out.println("Invalid Server Response");
	        lock.unlock();
	        //Send DESCRIBE message to the server
	        sendRequest("DESCRIBE");
	        lock.lock();
	        if (parseServerResponse("DESCRIBE") != 200)
	            System.out.println("Invalid Server Response");
	        lock.unlock();
	        
	      //Init non-blocking RTPsocket that will be used to receive data
	        try {
	            //construct a new DatagramSocket to receive RTP packets from the server, on port RTP_RCV_PORT
	            RTPsocket = new DatagramSocket(RTP_Client_PORT);
	            //UDP socket for sending QoS RTCP packets
	            RTCPsocket = new DatagramSocket(RTCP_Client_PORT);
	            //set TimeOut value of the socket to 5msec.
	            RTPsocket.setSoTimeout(5);
	        }
	        catch (SocketException se)
	        {
	            System.out.println("Socket exception: "+se);
	            System.exit(0);
	        }
	        
	        //Send SETUP message to the server
	        sendRequest("SETUP");
	
	        //Wait for the response 
	        lock.lock();
	        if (parseServerResponse("SETUP") != 200)
	            System.out.println("Invalid Server Response");
	        else 
	        {
	            //change RTSP state and print new state 
	            state = READY;
	            System.out.println("New RTSP state: READY");
	        }
	        lock.unlock();
	        
	    
	    }
	    else // The redirect is being performed
	    {
	    	 //Init non-blocking RTPsocket that will be used to receive data
	        try {
	           
	        	//construct a new DatagramSocket to receive RTP packets from the server, on port RTP_RCV_PORT
	            RTPsocket.close();
	            System.out.println("BEFORE RTPsocket setup");
	        	RTPsocket = new DatagramSocket(RTP_Client_PORT);
	        	System.out.println("RTPsocket setup DONE");
	            //UDP socket for sending QoS RTCP packets
	            RTCPsocket = new DatagramSocket(RTCP_Client_PORT);
	            System.out.println("RTCPsocket setup DONE");
	            //set TimeOut value of the socket to 5msec.
	            RTPsocket.setSoTimeout(5);
	        }
	        catch (SocketException se)
	        {
	            System.out.println("Socket exception: "+se);
	            System.exit(0);
	        } 
	
	      //Send OPTIONS message to the server
	        sendRequest("OPTIONS");
	        lock.lock();
	        if (parseServerResponse("OPTIONS") != 200)
	            System.out.println("Invalid Server Response");
	        lock.unlock();
	        //Send DESCRIBE message to the server
	        sendRequest("DESCRIBE");
	        lock.lock();
	        if (parseServerResponse("DESCRIBE") != 200)
	            System.out.println("Invalid Server Response");
	        lock.unlock();
	        
	        //Send SETUP message to the server
	        sendRequest("SETUP");
	        lock.lock();
	        //Wait for the response 
	        if (parseServerResponse("SETUP") != 200){
	            System.out.println("Invalid Server Response");
	            lock.unlock();
	        }
	        else 
	        {
	        	lock.unlock();
	            //change RTSP state and print new state 
	           // DO not change the state, so if it was playing 
	        	// state = READY;
	        	if (state == PLAYING){ //start resuming the video
	        		//Send PLAY message to the server
	        		
	                sendRequest("PLAY");
	                lock.lock();
	                //Wait for the response 
	                if (parseServerResponse("PLAY") != 200) {
	                    System.out.println("Invalid Server Response");
	                }
	                else {
	                    //change RTSP state and print out new state
	                    state = PLAYING;
	                    System.out.println("New RTSP state: RESUME PLAYING");

	                    //start the timer
	                    playback.startPlay();
	                    timer.start();
	                    rtcpSender.startSend();
	                    
	                }
	                lock.unlock();
	        	}
	        }
	    }
    }
    
    //------------------------------------
    //Handler for buttons
    //------------------------------------

    //Handler for Setup button
    //-----------------------
    class setupButtonListener implements ActionListener {

        public void actionPerformed(ActionEvent e){
            System.out.println("Setup Button pressed !");      
            setupConnection();
	        Thread rtspListener = new Thread(new Runnable() {
	     		public void run() {
	             	while(true){             		
	             		try {
							if(RTSPBufferedReader.ready() && lock.tryLock()){
								try{
								System.out.println("Do you acquire the lock?");
								boolean buff_flag = RTSPBufferedReader.ready();
								if (buff_flag && parseServerRedirect() != 1){
							        System.out.println("Invalid Server Redirect Command!");
							        lock.unlock();
							        //continue;
								}
								else if (buff_flag){
									lock.unlock();
									System.out.println("**********Do you release the lock???********************");
									//Now perform REDIRECT
									performRedirect();
								}
								else{
									lock.unlock();
								}
								}catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								finally{	
									//lock.unlock();
								}
							}
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
	             		
	 				} 
	     		}
	     	});
	            rtspListener.start();
	    }
    }

    
    //Handler for Play button
    //-----------------------
    class playButtonListener implements ActionListener {

        public void actionPerformed(ActionEvent e) {

            System.out.println("Play Button pressed!"); 

            //Start to save the time in stats
            statStartTime = System.currentTimeMillis();
            statLastPktTime = statStartTime;
            if (state == READY) {
                //increase RTSP sequence number
                RTSPSeqNb++;

                //Send PLAY message to the server
                try {
					if (lock.tryLock(100, TimeUnit.MILLISECONDS)){
						try{
					    	sendRequest("PLAY");
					        if (parseServerResponse("PLAY") != 200) {
					            System.out.println("Invalid Server Response");
					        }
					        else {
					            //change RTSP state and print out new state
					            state = PLAYING;
					            System.out.println("New RTSP state: PLAYING");

					            //start the timer
					            timer.start();
					            try {
					            	System.out.println("Buffering.................");
									Thread.sleep(500);
								} catch (InterruptedException e1) {
									// TODO Auto-generated catch block
									e1.printStackTrace();
								}
					            rtcpSender.startSend();
					            playback.startPlay();
					        }
						}finally{
							lock.unlock();
						}
					}
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
            }
            //else if state != READY then do nothing
            else{
            	System.out.println("PLAY BUTTON ERROR!");
            }
            
        }
    }

    //Handler for Pause button
    //-----------------------
    class pauseButtonListener implements ActionListener {

        public void actionPerformed(ActionEvent e){
        	//TODO: Add  a case when all the video is sent and the server connection is closed
        	
        	
            System.out.println("Pause Button pressed!");   

            if (state == PLAYING) 
            {
                //increase RTSP sequence number
                RTSPSeqNb++;

                //Send PAUSE message to the server
                lock.lock();
                sendRequest("PAUSE");

                //Wait for the response 
                //lock.lock();
                if (parseServerResponse("PAUSE") != 200)
                    System.out.println("Invalid Server Response");
                else 
                {
                    //change RTSP state and print out new state
                    state = READY;
                    System.out.println("New RTSP state: READY");
                      
                    //stop the timer
                    timer.stop();
                    playback.stopPlay();
                    rtcpSender.stopSend();
                    
                }
                lock.unlock();
            }
            //else if state != PLAYING then do nothing
        }
    }

    //Handler for Teardown button
    //-----------------------
    class tearButtonListener implements ActionListener {

        public void actionPerformed(ActionEvent e){

            System.out.println("Teardown Button pressed !");  

            //increase RTSP sequence number
            RTSPSeqNb++;

            //Send TEARDOWN message to the server
            sendRequest("TEARDOWN");

            //Wait for the response 
            lock.lock();
            if (parseServerResponse("TEARDOWN") != 200)
                System.out.println("Invalid Server Response");
            else {     
                //change RTSP state and print out new state
                state = INIT;
                System.out.println("New RTSP state: INIT");

                //stop the timer
                timer.stop();
                playback.stopPlay();
                rtcpSender.stopSend();
                

                //exit
                System.exit(0);
            }
            lock.unlock();
        }
    }

    // Get information about the data stream
    class describeButtonListener implements ActionListener {

        public void actionPerformed(ActionEvent e) {
            System.out.println("Sending DESCRIBE request");  

            //increase RTSP sequence number
            RTSPSeqNb++;

            //Send DESCRIBE message to the server
            //lock.lock();
            sendRequest("DESCRIBE");

            //Wait for the response 
            lock.lock();
            if (parseServerResponse("DESCRIBE") != 200) {
                System.out.println("Invalid Server Response");
            }
            else {     
                System.out.println("Received response for DESCRIBE");
            }
            lock.unlock();
        }
    }
    
    //------------------------------------
    //Handler for timer
    //------------------------------------
    class playBack implements ActionListener {

        private Timer pbTimer;
        int interval;

        public playBack(int interval) {
            this.interval = interval;
            pbTimer = new Timer(interval, this);
            pbTimer.setInitialDelay(0);
            pbTimer.setCoalesce(true);
        }

        public void run() {
            System.out.println("PlayBack Thread Running");
        }

        public void actionPerformed(ActionEvent e) {
        	//display the image as an ImageIcon object
            icon = new ImageIcon(fsynch.nextFrame());
            iconLabel.setIcon(icon);
        }

        // Start sending RTCP packets
        public void startPlay() {
            pbTimer.start();
        }

        // Stop sending RTCP packets
        public void stopPlay() {
            pbTimer.stop();
        }
    }
    
    class timerListener implements ActionListener {

        public void actionPerformed(ActionEvent e) {
          
            //Construct a DatagramPacket to receive data from the UDP socket
            rcvdp = new DatagramPacket(buf, buf.length);

            try {
                //receive the DP from the socket, save time for stats
                RTPsocket.receive(rcvdp);

                double curTime = System.currentTimeMillis();
                double interpktTime = curTime - statLastPktTime;
                
                statTotalPlayTime += curTime - statStartTime; 
                statStartTime = curTime;

                //create an RTPpacket object from the DP
                RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength(),logFile);
                int seqNb = rtp_packet.getsequencenumber();

                //this is the highest seq num received

                //print important header fields of the RTP packet received: 
                System.out.println("Got RTP packet with SeqNum # " + seqNb
                                   + " TimeStamp " + rtp_packet.gettimestamp() + " ms, of type "
                                   + rtp_packet.getpayloadtype());

                //print header bitstream:
                rtp_packet.printheader();

                //get the payload bitstream from the RTPpacket object
                int payload_length = rtp_packet.getpayload_length();
                byte [] payload = new byte[payload_length];
                rtp_packet.getpayload(payload);

                //compute stats and update the label in GUI
                statExpRtpNb++;
                if (seqNb > statHighSeqNb) {
                    statHighSeqNb = seqNb;
                    System.out.println("statHighSeq " + statHighSeqNb + " Seq Num " + seqNb);
                }
                if (statExpRtpNb != seqNb) {
                    statCumLost++;
                    System.out.println("statExpSeq " + statExpRtpNb + " Seq Num " + seqNb);
                    statExpRtpNb = seqNb;
                }
                statDataRate = statTotalPlayTime == 0 ? 0 : (statTotalBytes / (statTotalPlayTime / 1000.0));
                statFractionLost = (float)statCumLost / statHighSeqNb;
                statTotalBytes += payload_length;
                statLastPktTime = curTime;
                updateStatsLabel();

                //Log video quality data
                try {
    				statlogFile.write("Time," + curTime + ",seq Number," +seqNb + ",Server," + ServerIPAddr + ",interpkt," + interpktTime + ", CumLost, " + statCumLost + "\n");
    				statlogFile.flush();
    				plotlogFile.write((curTime - StartTime)/1000.0 + "," + interpktTime + "," + statCumLost + "\n");
    				plotlogFile.flush();
    			} catch (IOException e1) {
    				// TODO Auto-generated catch block
    				e1.printStackTrace();
    			}
                
                //get an Image object from the payload bitstream
                Toolkit toolkit = Toolkit.getDefaultToolkit();
                fsynch.addFrame(toolkit.createImage(payload, 0, payload_length), seqNb);
                
                //display the image as an ImageIcon object
                //icon = new ImageIcon(fsynch.nextFrame());
                //iconLabel.setIcon(icon);
            }
            catch (InterruptedIOException iioe) {
                System.out.println("Nothing to read "+ iioe);
            }
            catch (IOException ioe) {
                System.out.println("Exception caught: "+ioe);
            }
        }
    }

    //------------------------------------
    // Send RTCP control packets for QoS feedback
    //------------------------------------
    class RtcpSender implements ActionListener {

        private Timer rtcpTimer;
        int interval;

        // Stats variables
        private int numPktsExpected;    // Number of RTP packets expected since the last RTCP packet
        private int numPktsLost;        // Number of RTP packets lost since the last RTCP packet
        private int lastHighSeqNb;      // The last highest Seq number received
        private int lastCumLost;        // The last cumulative packets lost
        private float lastFractionLost; // The last fraction lost

        Random randomGenerator;         // For testing only

        public RtcpSender(int interval) {
            this.interval = interval;
            rtcpTimer = new Timer(interval, this);
            rtcpTimer.setInitialDelay(0);
            rtcpTimer.setCoalesce(true);
            randomGenerator = new Random();
        }

        public void run() {
            System.out.println("RtcpSender Thread Running");
        }

        public void actionPerformed(ActionEvent e) {

            // Calculate the stats for this period
            numPktsExpected = statHighSeqNb - lastHighSeqNb;
            numPktsLost = statCumLost - lastCumLost;
            lastFractionLost = numPktsExpected == 0 ? 0f : (float)numPktsLost / numPktsExpected;
            lastHighSeqNb = statHighSeqNb;
            lastCumLost = statCumLost;

            //To test lost feedback on lost packets
            // lastFractionLost = randomGenerator.nextInt(10)/10.0f;

          //  RTCPpacket rtcp_packet = new RTCPpacket(lastFractionLost, statCumLost, statHighSeqNb);
            RTCPpacket rtcp_packet = new RTCPpacket(0, 0, statHighSeqNb);
            int packet_length = rtcp_packet.getlength();
            byte[] packet_bits = new byte[packet_length];
            rtcp_packet.getpacket(packet_bits);

            try {
                DatagramPacket dp = new DatagramPacket(packet_bits, packet_length, ServerIPAddr, RTCP_Server_PORT);
                RTCPsocket.send(dp);
            } catch (InterruptedIOException iioe) {
              //  System.out.println("Nothing to read");
            } catch (IOException ioe) {
                System.out.println("Exception caught: "+ioe);
            }
        }

        // Start sending RTCP packets
        public void startSend() {
            rtcpTimer.start();
        }

        // Stop sending RTCP packets
        public void stopSend() {
            rtcpTimer.stop();
        }
    }

    //------------------------------------
    //Synchronize frames
    //------------------------------------
    class FrameSynchronizer {

        private ArrayDeque<Image> queue;
        private int bufSize;
        private int curSeqNb;
        private Image lastImage;
        private int lastRxFrame;

        public FrameSynchronizer(int bsize) {
            curSeqNb = 1;
            lastRxFrame = 0;
            bufSize = bsize;
            queue = new ArrayDeque<Image>(bufSize);
        }

        //synchronize frames based on their sequence number
        public void addFrame(Image image, int seqNum) {
            //lastRxFrame is for future paly commands
        	if (seqNum > lastRxFrame)
            	lastRxFrame = seqNum;
//        	if (seqNum < curSeqNb) {
//                queue.add(lastImage);
//            }
//            if (seqNum > curSeqNb) {
//            	System.out.println("%%%% curSeqNb: " + curSeqNb);
//                for (int i = curSeqNb; i < seqNum - 1; i++) {
//                    queue.add(lastImage);
//                }
                queue.add(image);
//            }
//            else {
//                queue.add(image);
//            }
        }
        /*	queue.add(image);
        }*/
        //get the next synchronized frame
        public Image nextFrame() {
            //curSeqNb++;
            //lastImage = queue.peekLast();
            //return queue.remove();
        	if (queue.size() > 0){
	        	curSeqNb++;
	            lastImage = queue.peekFirst();
	            System.out.println("**********QUEUE SIZE: "+queue.size());
	            return queue.removeFirst();
        	}
        	else if (lastImage != null){
        		curSeqNb++;
        		return lastImage;
        	}
        	else{
        		try {
					Thread.sleep(100);
					if (queue.size() > 0){
			        	curSeqNb++;
			            lastImage = queue.peekFirst();
			            System.out.println("**********QUEUE SIZE: "+queue.size());
			            return queue.removeFirst();
		        	}
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        		return lastImage;
        	}
        }
    }

    //------------------------------------
    //Parse Server Response
    //------------------------------------
    private int parseServerResponse(String request_type) {
        int reply_code = 0;

        try {
            //parse status line and extract the reply_code:
            String StatusLine = RTSPBufferedReader.readLine();
            System.out.println("RTSP Client - Received from Server:");
            System.out.println(StatusLine);
          
            StringTokenizer tokens = new StringTokenizer(StatusLine);
            tokens.nextToken(); //skip over the RTSP version
            reply_code = Integer.parseInt(tokens.nextToken());
            
            //if reply code is OK get and print the 2 other lines
            if (reply_code == 200) {
            	if (request_type == "OPTIONS"){
            		String SeqNumLine = RTSPBufferedReader.readLine();
                    System.out.println(SeqNumLine);
                    String PublicLine = RTSPBufferedReader.readLine();
                    System.out.println(PublicLine);
                    String ServerLine = RTSPBufferedReader.readLine();
                    System.out.println(ServerLine);
                    String DateLine = RTSPBufferedReader.readLine();
                    System.out.println(DateLine);
                    String SessionLine = RTSPBufferedReader.readLine();
                    System.out.println(SessionLine);

                    tokens = new StringTokenizer(SessionLine);
                    String temp = tokens.nextToken();
                    //if state == INIT gets the Session Id from the SessionLine
                    if (state == INIT && temp.compareTo("Session:") == 0) {
                        RTSPid = tokens.nextToken();
                    }
                 // read the additional CRLF
                	RTSPBufferedReader.readLine();

            	}
            	else if (request_type == "DESCRIBE"){
            		String SeqNumLine = RTSPBufferedReader.readLine();
                    System.out.println(SeqNumLine);
                    String ContentTypeLine = RTSPBufferedReader.readLine();
                    System.out.println(ContentTypeLine);
                    String ContentBaseLine = RTSPBufferedReader.readLine();
                    System.out.println(ContentBaseLine);
                    String ServerLine = RTSPBufferedReader.readLine();
                    System.out.println(ServerLine);
                    String DateLine = RTSPBufferedReader.readLine();
                    System.out.println(DateLine);
                    String LengthLine = RTSPBufferedReader.readLine();
                    System.out.println(LengthLine);
                    StringTokenizer tokenD = new StringTokenizer(LengthLine,": ");
                    tokenD.nextToken(); //Skip Content-Length
                    int length = Integer.parseInt(tokenD.nextToken());
                    RTSPBufferedReader.readLine(); // skip CRLF
                    String Describe="";
                    while (Describe.length() < length){
                    	Describe += RTSPBufferedReader.readLine();
                    	Describe += CRLF;
                    	//System.out.println(Describe + Describe.length());
                    }
                    
            	}
            	else if (request_type == "SETUP"){
            		System.out.println("Received SETUP request");
            		
            		String TransportLine = RTSPBufferedReader.readLine();
                    System.out.println(TransportLine); 
                    //retrieve server ports for RTCP and RTP connections
                    tokens = new StringTokenizer(TransportLine, ";=");
                    String token = tokens.nextToken();
                	while (token.compareTo("server_port") != 0){
                		token = tokens.nextToken();
                	}
                	token = tokens.nextToken();
                	StringTokenizer ports = new StringTokenizer(token,"-");
                	
                    RTP_Server_PORT = Integer.parseInt(ports.nextToken()); //skip RTP server port
                    RTCP_Server_PORT = Integer.parseInt(ports.nextToken());
                    String ServerLine = RTSPBufferedReader.readLine();
                    System.out.println(ServerLine);
                    String SeqNumLine = RTSPBufferedReader.readLine();
                    System.out.println(SeqNumLine);
                    String RTPInfoLine = RTSPBufferedReader.readLine();
                    System.out.println(RTPInfoLine);
                    String DateLine = RTSPBufferedReader.readLine();
                    System.out.println(DateLine);
                    String SessionLine = RTSPBufferedReader.readLine();
                    System.out.println(SessionLine);
                 // read the additional CRLF
                	RTSPBufferedReader.readLine();
            	}
            	else if (request_type == "PLAY"){
            		String SeqNumLine = RTSPBufferedReader.readLine();
                    System.out.println(SeqNumLine);
                 // read the additional CRLF
                	RTSPBufferedReader.readLine();
            	}
            	else if (request_type == "REDIRECT"){
            		/*TO BE FIXED AND REPLACED FOR SENDSERVERREDIRECT*/
            		  /*int reply_code = 0;
            		 

                    try {
                        //parse status line and extract the reply_code:
                        String StatusLine = RTSPBufferedReader.readLine();
                        System.out.println("RTSP Client - Received from Server:");
                        System.out.println(StatusLine);
                      
                        StringTokenizer tokens = new StringTokenizer(StatusLine);
                        String token = tokens.nextToken(); 
                        if (token.compareTo("REDIRECT")==0){
                        	reply_code = 1;
                        }
                        else 
                        	return -1;
                        if (reply_code == 1) {
                            String SeqNumLine = RTSPBufferedReader.readLine();
                            System.out.println(SeqNumLine);
                            String redirectLine = RTSPBufferedReader.readLine();
                            System.out.println(redirectLine);
                            StringTokenizer tokensRedirect = new StringTokenizer(redirectLine," :/");
                            tokensRedirect.nextToken();//skip Location: keyword
                            tokensRedirect.nextToken();//skip rtsp://
                            this.ServerIPAddr = InetAddress.getByName(tokensRedirect.nextToken());
                            this.RTSP_server_port = Integer.parseInt(tokensRedirect.nextToken());
                        }
                    } catch(Exception ex) {
                        System.out.println("Exception caught: "+ex);
                        System.exit(0);
                    }
                    return(reply_code);*/
            	}
            	else if(request_type == "PAUSE"){
            		String SeqNumLine = RTSPBufferedReader.readLine();
                    System.out.println(SeqNumLine);
                 // read the additional CRLF
                	RTSPBufferedReader.readLine();
            	}
            	
            }
            //else{
            	
            //	System.out.println("TEST**************");
            //}
        } catch(Exception ex) {
            System.out.println("Exception caught: "+ex);
            System.exit(0);
        }
      
        return(reply_code);
    }

    //------------------------------------
    //Parse Server Response Redirect
    //------------------------------------
    private int parseServerRedirect() throws Exception {
        int reply_code = 0;

        try {
            //parse status line and extract the reply_code:
            String StatusLine = RTSPBufferedReader.readLine();
            System.out.println("RTSP Client - Received from Server:");
            System.out.println(StatusLine);
          
            StringTokenizer tokens = new StringTokenizer(StatusLine);
            String token = tokens.nextToken(); 
            if (token.compareTo("REDIRECT")==0){
            	reply_code = 1;
            }
            else 
            	return -1;
            if (reply_code == 1) {
                String SeqNumLine = RTSPBufferedReader.readLine();
                System.out.println(SeqNumLine);
                String redirectLine = RTSPBufferedReader.readLine();
                System.out.println(redirectLine);
                StringTokenizer tokensRedirect = new StringTokenizer(redirectLine," :/");
                tokensRedirect.nextToken();//skip Location: keyword
                tokensRedirect.nextToken();//skip rtsp://
                this.ServerIPAddr = InetAddress.getByName(tokensRedirect.nextToken());
                System.out.println("Server IP address: "+ this.ServerIPAddr);
                this.RTSP_server_port = Integer.parseInt(tokensRedirect.nextToken());
            }
        } catch(Exception ex) {
            System.out.println("Exception caught: "+ex);
            System.exit(0);
        }
        return(reply_code);
    }
    private void performRedirect() throws Exception{
    	
    	System.out.println("start server redirect : "+this.ServerIPAddr+":"+this.RTSP_server_port);
	  //increase RTSP sequence number
        RTSPSeqNb++;
	    //Send TEARDOWN message to the server
        System.out.println("Teardown the connection");
		sendRequest("TEARDOWN");
		
		//Wait for the response 
		lock.lock();
		if (parseServerResponse("TEARDOWN") != 200)
		    System.out.println("Invalid Server Response");
		lock.unlock();
		
		timer.stop();
		playback.stopPlay();
	    rtcpSender.stopSend();
	    // Choose next port numbers for the new connection
	    RTP_Client_PORT = RTP_Client_PORT + 2;
	    RTCP_Client_PORT = RTCP_Client_PORT + 2;
	    System.out.println("TIMERS ARE STOPPED FOR REDIRECT: CURRENT STATE " + state);
		Setup_Server_Connection(this, VideoFileName);
		System.out.println("SETUP FINE!");
		setupConnection();
		System.out.println("SETUP CONNECTION IS FINE!");
    }
    
    private void updateStatsLabel() {
        DecimalFormat formatter = new DecimalFormat("###,###.##");
        statLabel1.setText("Total Bytes Received: " + statTotalBytes);
        statLabel2.setText("Packet Lost Rate: " + formatter.format(statFractionLost));
        statLabel3.setText("Data Rate: " + formatter.format(statDataRate) + " bytes/s");
    }

    //------------------------------------
    //Send RTSP Request
    //------------------------------------

    private void sendRequest(String request_type) {
        try {
            //Use the RTSPBufferedWriter to write to the RTSP socket

            //write the request line:
            RTSPBufferedWriter.write(request_type + " " + RTSP_ADDR + " RTSP/1.0" + CRLF);

            //write the CSeq line: 
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
            RTSPBufferedWriter.write("User-Agent: " + Client_Name + CRLF);
            

            //check if request_type is equal to "SETUP" and in this case write the 
            //Transport: line advertising to the server the port used to receive 
            //the RTP packets RTP_RCV_PORT
            if (request_type == "OPTIONS"){
            	//Do Nothing
            }
            else if (request_type == "SETUP") {
                RTSPBufferedWriter.write("Transport: RTP/AVP;unicast;client_port=" + RTP_Client_PORT+"-"+RTCP_Client_PORT + CRLF);
            }
            else if (request_type == "DESCRIBE") {
                RTSPBufferedWriter.write("Accept: application/sdp" + CRLF);
            }
            else if(request_type == "PLAY"){
            	RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
            	System.out.println("***************"+fsynch.lastRxFrame + 1);
            	RTSPBufferedWriter.write("Range: npt="+(fsynch.lastRxFrame) +"-"+ CRLF);
            }
            else {
                //otherwise, write the Session line from the RTSPid field
                RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
            }

            //An additional CRLF for specifying the end of request
            RTSPBufferedWriter.write(CRLF);
            RTSPBufferedWriter.flush();
        } catch(Exception ex) {
            System.out.println("Exception caught: "+ex);
            System.exit(0);
        }
    }    
}
