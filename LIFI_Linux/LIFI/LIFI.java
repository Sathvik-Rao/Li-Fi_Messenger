/*

author        	->  Sathvik rao
h/w start date 	-> 8/12/2019
s/w start date  ->  30/01/2020
compile    	->  javac LIFI.java
run           	->  javaw LIFI  (or) java LIFI

java version "1.8.0_241"
Java(TM) SE Runtime Environment (build 1.8.0_241-b07)
Java HotSpot(TM) 64-Bit Server VM (build 25.241-b07, mixed mode)

MySQL (Database) version -> 8.0.18
localhost:3306
root
dbpassword

28 -> .class files
12 -> icons
3 -> text files
*/

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.JOptionPane;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.KeyStroke;
import javax.swing.JToggleButton;
import javax.swing.ImageIcon;
import javax.swing.JTabbedPane;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.JPasswordField;

import java.awt.Font;

import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.FlowLayout;

import java.awt.Image;

import javax.swing.BorderFactory;
import javax.swing.border.Border;
import java.awt.Color;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

import java.io.FileDescriptor;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.RandomAccessFile;

import java.awt.Graphics;
import java.awt.event.*;
import java.util.Enumeration;

import java.net.URI;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;

import java.util.Arrays;
import java.util.Calendar;
import java.text.DateFormat; 
import java.text.SimpleDateFormat;
import javax.swing.text.DefaultCaret;
import javax.swing.JFileChooser; 

//global receiver TextArea
class Global
{
	public static volatile JTextArea receiveMessageTextArea = new JTextArea();
	public static volatile boolean offline = false;  //offline/online
	public static String portName = null;		//string port name 
	static RandomAccessFile fileSettings = null; 	//setting info file
	static String[] settingFileInfo ;		//array of setting info file details
	static RandomAccessFile fileUsers = null;	//users info file
	static RandomAccessFile fileDB = null;		//database info file

	//database
	static volatile Connection con = null;	
	static volatile Statement stmt = null;
	static volatile ResultSet rs = null;
	static final String DATABASENAME = "LIFIDATA";
	static volatile String currentUserID = "";

	
	static String senderID = "";   
	static volatile OutputStream o;
	static volatile InputStream i;
	static volatile byte[] msgBytes ;
	static volatile String msg;
	static volatile boolean sendFlag = false;
	static volatile boolean sendTimer = false;
	static volatile JTextArea sendMessageTextArea ;
	static volatile int runningBaudRateText ;
	static volatile DateFormat formatter;
	static final byte KEY = 4;
	static File sendFile = null;

	Global()
	{
		try 
		{
			fileSettings = new RandomAccessFile("lifiSettings.txt", "rw");
			fileUsers = new RandomAccessFile("userData.txt", "rw");

			//string array of lifi setting
			if(Global.fileSettings.length() != 0)
			   settingFileInfo  = new String[]  {Global.fileSettings.readLine(), Global.fileSettings.readLine()};
			else
			    settingFileInfo  = new String[] {"", ""};

			//assign sender ID
			senderID = settingFileInfo[1];
			Global.formatter = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
		}
		catch(Exception e) 
		{
			JOptionPane.showMessageDialog(new JFrame(), e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
}

//Receiver Thread
class SerialReader implements Runnable 
{
	byte[] buffer = new byte[200];
	byte[] bufferText = new byte[52428800]; //50MB
	String msgReceived;
	int ascii = 0, x = 0, a = 0, getAscii = 0,y=0;
	byte tempParity = 0;
	boolean parityBoolean = false;
	String parityText;

        	public void run()
        	{	
            		try
		{
			while(true) 
			{
				
					if(Global.i.read() == 1)  //headers
					{
						if(Global.offline)
						{
							x = 0;
							Global.sendTimer = true;  //used for sending
							long startTime1 = System.currentTimeMillis(); 
							while ( ( ascii = Global.i.read()) != 3 )  
							{
								if( (System.currentTimeMillis()-startTime1) < 500 )
								  buffer[x++]=(byte)ascii;
								else
								  break;
								startTime1 = System.currentTimeMillis();
							}
						
							String parity = new String(buffer,66,1);  //for buffer start at 66(+1) and length 
							//To check header parity 
							parityBoolean = false ;
							tempParity = buffer[66];
							buffer[66] = 0;
  							for (byte b : buffer)	//each character
 							{
 								for (int i = 0; i < 8; i++)	//8bit ascii
  								{
									//128 = 0b10000000  
									if((b & 128) != 0)
									{
										parityBoolean = !parityBoolean;   // changes alternatively
									}
									b <<= 1;  //left shift by 1
								}
							}
							if( ((parity.equals("1")) && (parityBoolean)) || ((parity.equals("0")) && (!parityBoolean)) ) //execute if parity successful
							{
								if( Global.senderID.equals(new String(buffer,32,32)) ) //check is it for me
								{
									//checks sender ID from fileUsers
									Global.fileUsers.seek(0);
									String temp = new String(buffer,0,32); //sender ID
									String ReceiverName;
									while( (ReceiverName=Global.fileUsers.readLine()) != null)
									{
										if( temp.equals(Global.fileUsers.readLine()) ) //user granted
										{
											//TEXT
											if( "0".equals(new String(buffer,64,1)) && "0".equals(new String(buffer,65,1)) && x==67 )  //00 ->send text ack
											{
												//send acknowledge
												//parity will not change for ack as ascii code is 00000011.
												buffer[66]=tempParity;	//set parity again
												byte swap = 0;
												for(int a=0,b=32 ; a<32 ; a++,b++)
												{
													swap = buffer[a];
													buffer[a] = buffer[b];
													buffer[b] = swap;
												}
												String z = new String(buffer,0,x);
												z +="6";
												Global.o.write( new byte[]{0,0,1} ); //start of header
												Global.o.write(z.getBytes());	//received header
												Global.o.write( new byte[]{3} );  //ack,end

												//receive text
												getAscii = 0;
												y=0;
												long startTime2 = System.currentTimeMillis();
												startTime2 += ( ( ((buffer.length + 5) * 8) / Global.runningBaudRateText ) * (1000) ) + ( 6000 + Global.runningBaudRateText ); 
												while(true)
												{
													if(System.currentTimeMillis() < startTime2)
													{
														if(Global.i.read() == 2)  //start of text
														{
															long startTime = System.currentTimeMillis(); 
															while ( (getAscii = Global.i.read()) != 3 )
															{
																if( (System.currentTimeMillis()-startTime) < 500 )
												    	 			  bufferText[y++]=(byte)getAscii;
																else
																  break;
																startTime = System.currentTimeMillis();
															}
															parityText = new String(bufferText,(y -1),1); //get parity of text
															bufferText[y-1] = 0;
															parityBoolean = false ; 
															for (byte b : bufferText)	//each character
															{
																for (int i = 0; i < 8; i++)	//8bit ascii
  																{
																	//128 = 0b10000000  
																	if((b & 128) != 0)
																	{
																		parityBoolean = !parityBoolean;   // changes alternatively
																	}
																	b <<= 1;  //left shift by 1
   																}
  															}
															if( ((parityText.equals("1")) && (parityBoolean)) || ((parityText.equals("0")) && (!parityBoolean)) ) //execute if parity successful
															{
																try
																{	
																	Global.o.write( new byte[]{0,1,4} );	//end transmission
																	byte[] msgBytesDecrypted = new byte[bufferText.length];
												
																	for(int g = 0 ; g < bufferText.length ; g++)
																	{
											 							msgBytesDecrypted[g] = bufferText[g] ;
																		msgBytesDecrypted[g] += Global.KEY;
																	}
																	msgReceived = new String(msgBytesDecrypted,0,(y-1));
																	Calendar calendar = Calendar.getInstance();
																	if( Global.currentUserID.equals( new String(buffer,32,32)) ) //chat box opened
																	{
																		Global.receiveMessageTextArea.append( "\n < "+ ReceiverName + " [" + Global.formatter.format(calendar.getTime()) + "]\n" + msgReceived+ "\n\n" );
																		Global.stmt.executeUpdate("insert into `"+ Global.currentUserID +"` values(\""+ "\n < " + ReceiverName +" ["+ Global.formatter.format(calendar.getTime()) + "]\n" +msgReceived +"\");");
																	}
																	else
																	{
																		Global.stmt.executeUpdate("insert into `"+ new String(buffer,32,32) +"` values(\""+ "\n < " + ReceiverName +" ["+ Global.formatter.format(calendar.getTime()) + "]\n" + msgReceived +"\");");
																		ImageIcon receivedMessageIcon = new ImageIcon("icons/envelope.png");
																		Image image = receivedMessageIcon.getImage(); // transform it 
																		Image newimg = image.getScaledInstance(40, 40,  java.awt.Image.SCALE_SMOOTH);
																		receivedMessageIcon = new ImageIcon(newimg);  // transform it back
																		JOptionPane.showMessageDialog(new JFrame(), "Message received from "+ReceiverName, "New Message", JOptionPane.INFORMATION_MESSAGE, receivedMessageIcon );
																	}													
																}
																catch(Exception e)
																{
																	JOptionPane.showMessageDialog(new JFrame(),e.toString(),"Error", JOptionPane.ERROR_MESSAGE); 
																}
															}
															else
														 	 Global.o.write( new byte[]{0,1,5} ); 	//end transmission
														
															Arrays.fill(bufferText, (byte)0); //refresh buffer by placing null
															break;
														}
													}
													else
													{
														Global.o.write( new byte[]{0});
														break;
													}
												
												}
											}
											else if("0".equals(new String(buffer,64,1)) && "0".equals(new String(buffer,65,1)) && "6".equals(new String(buffer,67,1)) && x== 68) //received text ack
											{
												//send text (payload)
												parityBoolean = false ;
												byte[] msgBytesEncrypted = new byte[Global.msgBytes.length];
											
												for(int g = 0 ; g < Global.msgBytes.length ; g++)
												{
												 	msgBytesEncrypted[g] = Global.msgBytes[g] ;
													msgBytesEncrypted[g] -= Global.KEY;
												}
												for (byte b : msgBytesEncrypted)	//each character
												{
													for (int i = 0; i < 8; i++)	//8bit ascii
  													{
														//128 = 0b10000000  
														if((b & 128) != 0)
														{
															parityBoolean = !parityBoolean;   // changes alternatively
														}
														b <<= 1;  //left shift by 1
													}
   												}
												if(parityBoolean) //odd parity
												{
													Global.o.write(new byte[]{2}); //start of text
													Global.o.write(msgBytesEncrypted); //text message(payload)
													Global.o.write(new byte[]{49,3}); // 1 (parity) ,end of text
												}
												else
												{
													Global.o.write(new byte[]{2});
													Global.o.write(msgBytesEncrypted);
													Global.o.write(new byte[]{48,3}); // 0 ,end of text
												}
												Arrays.fill(buffer, (byte)0); //refresh buffer by placing null
												long startTime = System.currentTimeMillis();
												startTime += ( ( ((msgBytesEncrypted.length + 3) * 8) / Global.runningBaudRateText ) * 1000 ) + ( 10000 + Global.runningBaudRateText );
												while(true) //get end
												{
													if(System.currentTimeMillis() < startTime)
													{
														if( Global.i.read() == 1 )
														{
															x = 0;
															buffer[x]=(byte)Global.i.read();
															Calendar calendar = Calendar.getInstance();
															if (buffer[0] == 4) //end Transmission
															{
																Global.receiveMessageTextArea.append("\n > You [" + Global.formatter.format(calendar.getTime()) + "]\n" +Global.msg+"\n");  //send message to receiver text area
																try
																{
																	Global.stmt.executeUpdate("insert into `"+ Global.currentUserID +"` values(\""+ "\n > You [" + Global.formatter.format(calendar.getTime()) + "]\n" + Global.msg +"\");");
																}
																catch(Exception e) 
																{
																	JOptionPane.showMessageDialog(new JFrame(),e.toString(),"Error", JOptionPane.ERROR_MESSAGE);
																}
																Global.sendMessageTextArea.setText("");  //empty sender text area
															}
															else if (buffer[0] == 5)
															   Global.receiveMessageTextArea.append("Error Sending\n");
															break;
														}
													}
													else
													{
														Global.o.write(new byte[]{0});
														break;
													}
											 	 }	
											}
											else if( "0".equals(new String(buffer,64,1)) && "1".equals(new String(buffer,65,1)) && "0".equals(new String(buffer,67,1)) && x > 72 )  //01 ->receive file header
											{
												//send file acknowledge
												//parity will not change for ack as ascii code is 00000011.
												buffer[66]=tempParity;	//set parity again
												byte swap = 0;
												for(int a=0,b=32 ; a<32 ; a++,b++)
												{
													swap = buffer[a];
													buffer[a] = buffer[b];
													buffer[b] = swap;
												}
												StringBuffer z = new StringBuffer(new String(buffer,0,x));
												z.insert(67,'6');	//add ack
												Global.o.write( new byte[]{0,0,1} ); //start of header
												Global.o.write(z.toString().getBytes());	//received header, ack
												Global.o.write( new byte[]{3} );  //end

												//receive file
												getAscii = 0;
												y=0;
												try
												{
													String fileName = z.substring(z.lastIndexOf("/") + 1);

													String path =  System.getProperty("user.dir") + "/userData/" + ReceiverName;
													path = path.replace('\\' , '/');
													new File(path).mkdirs(); //create folders if not present
													FileOutputStream fos=new FileOutputStream(path +"/"+ fileName);
													long startTime2 = System.currentTimeMillis();
													startTime2 += ( ( ((z.length() + 4) * 8) / Global.runningBaudRateText ) * (1000) ) + ( 6000 + Global.runningBaudRateText );
													while(true)
													{
														if(System.currentTimeMillis() < startTime2)
														{	
															if(Global.i.read() == 2)  //start of file
															{	
																long startTime = System.currentTimeMillis();
																long l = Long.parseLong(z.substring(68, z.lastIndexOf("/")));
																while ( l > 0 )
																{
																	if( (System.currentTimeMillis()-startTime) < 500 )
																	  fos.write(Global.i.read());
																	else
																	  break;
																	startTime = System.currentTimeMillis();
																	l--;
																}
																if( fos.getChannel().size() == Long.parseLong(z.substring(68, z.lastIndexOf("/"))) )
																{	
																	Global.o.write( new byte[]{0,1,4} );	//end transmission
																	Calendar calendar = Calendar.getInstance();
																	if( Global.currentUserID.equals( new String(buffer,32,32)) ) //chat box opened
																	{
																		Global.receiveMessageTextArea.append( "\n < "+ ReceiverName + " [" + Global.formatter.format(calendar.getTime()) + "]\n " + fileName + " (" + path + "/" + fileName + ")\n\n" );
																		Global.stmt.executeUpdate("insert into `"+ Global.currentUserID +"` values(\""+ "\n < " + ReceiverName +" ["+ Global.formatter.format(calendar.getTime()) + "]\n " + fileName + " (" + path + "/" + fileName +")\");");
																	}
																	else
																	{
																		Global.stmt.executeUpdate("insert into `"+ new String(buffer,32,32) +"` values(\""+ "\n < " + ReceiverName +" ["+ Global.formatter.format(calendar.getTime()) + "]\n " + fileName + " (" + path + "/" + fileName +")\");");
																		ImageIcon receivedMessageIcon = new ImageIcon("icons/envelope.png");
																		Image image = receivedMessageIcon.getImage(); // transform it 
																		Image newimg = image.getScaledInstance(40, 40,  java.awt.Image.SCALE_SMOOTH);
																		receivedMessageIcon = new ImageIcon(newimg);  // transform it back
																		JOptionPane.showMessageDialog(new JFrame(), "File received from "+ReceiverName, "New Message", JOptionPane.INFORMATION_MESSAGE, receivedMessageIcon );
																	}												
																}
																else
																  Global.o.write( new byte[]{0,1,5} );
																fos.close();
																break;
															}
														}
														else
														{
															Global.o.write( new byte[]{0});
															break;
														}
													}
												}
												catch(Exception e)
												{
													JOptionPane.showMessageDialog(new JFrame(),e.toString(),"Error", JOptionPane.ERROR_MESSAGE); 
												}
											}
											else if("0".equals(new String(buffer,64,1)) && "1".equals(new String(buffer,65,1)) && "6".equals(new String(buffer,67,1)) && x > 72) //received file ack
											{
												//send file (payload)
												try
												{
													FileInputStream fis=new FileInputStream(Global.sendFile.getAbsolutePath());
													int i;
													Global.o.write(new byte[]{2});
													while( (i=fis.read()) != -1 )
													  Global.o.write(i);

													Arrays.fill(buffer, (byte)0); //refresh buffer by placing null
													long startTime = System.currentTimeMillis();
													startTime += ( ( ((Global.sendFile.length() + 1) * 8) / Global.runningBaudRateText ) * 1000 ) + ( 10000 + Global.runningBaudRateText );
													while(true) //get end
													{
														if(System.currentTimeMillis() < startTime)
														{
															if( Global.i.read() == 1 )
															{
																buffer[0]=(byte)Global.i.read();
																Calendar calendar = Calendar.getInstance();
																if (buffer[0] == 4) //end Transmission
																{
																	Global.receiveMessageTextArea.append("\n > You [" + Global.formatter.format(calendar.getTime()) + "]\nSent " + Global.sendFile.getName() +"\n");  //send message to receiver text area
																	Global.stmt.executeUpdate("insert into `"+ Global.currentUserID +"` values(\""+ "\n > You [" + Global.formatter.format(calendar.getTime()) + "]\nSent " + Global.sendFile.getName() +"\");");
																	Global.sendMessageTextArea.setText("");  //empty sender text area
																}
																else if (buffer[0] == 5)
																  Global.receiveMessageTextArea.append("Error Sending\n");
																break;
															}
														}
														else
														{
															Global.o.write(new byte[]{0});
															break;
														}
											  		}
												}
												catch(Exception e)
												{
													JOptionPane.showMessageDialog(new JFrame(), e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
												}
											}
											break;
										}
									}
								}	
							}
							else
							{
								if(Global.sendFlag)
								  Global.receiveMessageTextArea.append("Error sending\n");
								else
								{
									Global.o.write(new byte[]{1});
									try
									{
										Thread.sleep(1000);
									}
									catch(Exception e)
									{
										JOptionPane.showMessageDialog(new JFrame(), e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
									}
									Global.o.write(new byte[]{0});
								}
							}
							Global.sendFlag = false;
							Arrays.fill(buffer, (byte)0); //refresh buffer by placing null
						} 
					}
				
			}
		}
		catch(IOException e)
		{
			JOptionPane.showMessageDialog(new JFrame(),"Receiver Error\n"+e.toString(),"Error", JOptionPane.ERROR_MESSAGE); //Dialog box
		}
	}
}

//port selection frame (starting)
class PortSelect
{
	JTextField hostTextField = null;
	JTextField usernameTextField = null;
	JPasswordField passwordTextField = null;

	PortSelect()
	{
		ImageIcon unpluggedIcon = new ImageIcon("icons/unplugged.png");
		Image image = unpluggedIcon.getImage(); // transform it 
		Image newimg = image.getScaledInstance(40, 40,  java.awt.Image.SCALE_SMOOTH);
		unpluggedIcon = new ImageIcon(newimg);  // transform it back

		JFrame jfrm = new JFrame("Scan Port");
		jfrm.setIconImage(unpluggedIcon.getImage());
		jfrm.setLayout(new GridBagLayout());
		jfrm.setSize(500,300);
		GridBagConstraints c = new GridBagConstraints();

		jfrm.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		//port Label
		JLabel portLabel = new JLabel("Select port");
		c.gridx = 0;
		c.gridy = 0;
		jfrm.add(portLabel ,c);
		
		//port scan button
		JButton scanButton = new JButton("Scan");
		c.gridx = 1;
		c.gridy = 0;
		jfrm.add(scanButton, c);

		//port scan event
		JComboBox<String> jcb = new JComboBox<String>();
		scanButton.addActionListener( new ActionListener() 
		{	@Override
			public void actionPerformed(ActionEvent ae)
			{
				Enumeration<?> ports = CommPortIdentifier.getPortIdentifiers();
				jcb.removeAllItems();	//clear combobox for each scan action
				while(ports.hasMoreElements())  
     	          		   	 {
					jcb.addItem(((CommPortIdentifier)ports.nextElement()).getName());  //add values to combobox
				 }		
			}
		});
		
		//port combobox
		c.gridx = 2;
		c.gridy = 0;
		jfrm.add(jcb, c);

		//database Label
		try
		{
			Global.fileDB = new RandomAccessFile("databaseCredentials.txt", "rw");

			JLabel hostLabel = new JLabel("host:port");
			c.gridx = 0;
			c.gridy = 1;
			jfrm.add(hostLabel ,c);	

			hostTextField = new JTextField(14);
			c.gridx = 1;
			c.gridy = 1;
			c.gridwidth = 2;
			hostTextField.setText(Global.fileDB.readLine());
			jfrm.add(hostTextField , c);

			JLabel usernameLabel = new JLabel("username");
			c.gridx = 0;
			c.gridy = 2;
			c.gridwidth = 1;
			jfrm.add(usernameLabel ,c);	

			usernameTextField = new JTextField(14);
			c.gridx = 1;
			c.gridy = 2;
			c.gridwidth = 2;
			usernameTextField.setText(Global.fileDB.readLine());
			jfrm.add(usernameTextField , c);

			JLabel passwordLabel = new JLabel("password");
			c.gridx = 0;
			c.gridy = 3;
			c.gridwidth = 1;
			jfrm.add(passwordLabel ,c);	

			passwordTextField = new JPasswordField (14);
			c.gridx = 1;
			c.gridy = 3;
			c.gridwidth = 2;
			passwordTextField.setText(Global.fileDB.readLine());
			jfrm.add(passwordTextField , c);

		}
		catch(Exception e)
		{
			JOptionPane.showMessageDialog(new JFrame(), e.toString(), "Error", JOptionPane.ERROR_MESSAGE); 
		}

		JButton launchButton = new JButton("Launch");
		c.gridx = 2;
		c.gridy = 4;
		c.gridwidth = 1;
		jfrm.add(launchButton, c);
	
		launchButton.addActionListener( new ActionListener() 
		{	@Override
			public void actionPerformed(ActionEvent ae)
			{
				if(jcb.getItemCount() > 0 && !(hostTextField.getText().isEmpty()) && !(usernameTextField.getText().isEmpty()) && !(String.valueOf(passwordTextField.getPassword()).isEmpty()) ) 
				{
					try
					{
						Global.portName = jcb.getSelectedItem().toString();	//get port name
						new RandomAccessFile("databaseCredentials.txt","rw").setLength(0);  //clear the content
						Global.fileDB.seek(0);
						Global.fileDB.writeBytes(hostTextField.getText()+"\n"+usernameTextField.getText()+"\n"+String.valueOf(passwordTextField.getPassword())+"\n");
						
						//connect to database 
						Class.forName("com.mysql.cj.jdbc.Driver");
						Global.con = DriverManager.getConnection("jdbc:mysql://" + hostTextField.getText() + "/?" , usernameTextField.getText() , String.valueOf(passwordTextField .getPassword()));  //"jdbc:mysql://localhost:3306/?","root","dbpassword"
						Global.stmt = Global.con.createStatement();
						Global.stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS "+ Global.DATABASENAME +";"); 		//database name LIFIDATA (It check if the database exists and if it doesn't it will create the new database.)
						Global.stmt.executeUpdate("use "+Global.DATABASENAME);

						SwingUtilities.invokeLater(new Runnable() { public void run() { new LIFI(); } } );	//start LIFI Messenger
						jfrm.setVisible(false);
						jfrm.dispose();	//close it when launched
					}
					catch(Exception e)
					{
						JOptionPane.showMessageDialog(new JFrame(), e.toString(), "Error", JOptionPane.ERROR_MESSAGE); 
					}
				}
				else
				{
					JOptionPane.showMessageDialog(new JFrame(), "Enter all MySQL credentials or Scan port", "Error", JOptionPane.ERROR_MESSAGE); 
				}	
			}
		});
		
		jfrm.setVisible(true);
	}
}

//adding new user or removing
class NewUser
{
	NewUser()
	{
		JFrame jfrm2 = new JFrame("User");
		ImageIcon add = new ImageIcon("icons/add.png");
		jfrm2.setIconImage(add.getImage());  //frame icon
		jfrm2.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		jfrm2.setSize(1000,500);
		//jfrm2.setExtendedState( jfrm1.getExtendedState()|JFrame.MAXIMIZED_BOTH );

		jfrm2.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();

		//name label
		JLabel nameLabel = new JLabel("Name: ");
		c.gridx = 0;
		c.gridy = 0;
		jfrm2.add(nameLabel ,c);

		//name text field
		JTextField nameTextField = new JTextField(32);  //32 columns(characters) visible space
		c.gridx = 1;
		c.gridy = 0;
		jfrm2.add(nameTextField , c);

		//id label
		JLabel idLabel = new JLabel("ID: ");
		c.gridx = 0;
		c.gridy = 1;
		jfrm2.add(idLabel ,c);

		//id text field
		JTextField idTextField = new JTextField(32);  //32 columns(characters) visible space
		c.gridx = 1;
		c.gridy = 1;
		jfrm2.add(idTextField , c);
		idTextField.addKeyListener(new KeyAdapter() {	//event for limiting ID
        			@Override
        			public void keyTyped(KeyEvent e) 
			{
            				if (idTextField.getText().length() > 31  ) // limit to 32 characters 
			                e.consume();
        			}
    		});

		//add button
		JButton addButton = new JButton("Add");
		c.gridx = 1;
		c.gridy = 2; 
		c.fill = GridBagConstraints.HORIZONTAL;
		jfrm2.add(addButton , c);

		//delete button
		JButton deleteButton = new JButton("Delete (ID Required)");
		c.gridx = 1;
		c.gridy = 3;
		jfrm2.add(deleteButton , c);

		//show ID button
		JButton showIDButton = new JButton("show ID(name Required)");
		c.gridx = 1;
		c.gridy = 4;
		jfrm2.add(showIDButton , c);


		//add button event
		addButton.addActionListener( new ActionListener() 
		{	@Override
			public void actionPerformed(ActionEvent ae)
			{
				if(nameTextField.getDocument().getLength() == 0)
				{
					JOptionPane.showMessageDialog(new JFrame(), "Name cannot be blank..!", "Error", JOptionPane.ERROR_MESSAGE); 
				}
				else if(idTextField.getDocument().getLength() != 32)
				{
					JOptionPane.showMessageDialog(new JFrame(), "Enter valid ID..!", "Error", JOptionPane.ERROR_MESSAGE); 
				}
				else
				{
					try
					{	
						//create table
						Global.stmt.executeUpdate( "create table `"+idTextField.getText()+"` (dataDB text(52428800));" ); //create table `___` (dataDB text(52428800));
						
						Global.fileUsers.seek(Global.fileUsers.length()); //point to end of file
						Global.fileUsers.writeBytes(nameTextField.getText()+"\n"+idTextField.getText()+"\n");	//write user data into file

						//added new user dialog box
						ImageIcon addIcon = new ImageIcon("icons/add.png");
						Image image = addIcon.getImage(); // transform it 
						Image newimg = image.getScaledInstance(40, 40,  java.awt.Image.SCALE_SMOOTH);
						addIcon = new ImageIcon(newimg);  // transform it back
						JOptionPane.showMessageDialog(new JFrame(), (nameTextField.getText()+"\n"+idTextField.getText()+"\nRestart required"), "Added", JOptionPane.INFORMATION_MESSAGE, addIcon);

						nameTextField.setText("");	//clear name field
						idTextField.setText("");	//clear id field

					}
					catch(Exception e)
					{
						JOptionPane.showMessageDialog(new JFrame(), e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
					}
				}
			}
		});

		//delete event
		deleteButton.addActionListener( new ActionListener() 
		{	
			@Override
			public void actionPerformed(ActionEvent ae)
			{
				if(idTextField.getDocument().getLength() == 32)
				{
					try
					{
						int count = 0, flag = 0;

						Global.fileUsers.seek(0);
						while(Global.fileUsers.readLine()!= null)
					 	 count++;
					
						Global.fileUsers.seek(0);
						String temp1 = "",temp2 = "",result = "";
						for(int i=0;i<(count/2);i++)
						{
							temp1 = 	Global.fileUsers.readLine();
							temp2 = Global.fileUsers.readLine();
							if( temp2.equals(idTextField.getText()) ) 
							   flag = 1;
							else
					 		    result += temp1+"\n"+temp2+"\n";
						}
						if(flag == 1)
						{
							//delete table
							Global.stmt.executeUpdate( "drop table `"+idTextField.getText()+"`;" ); //drop tabel `______`

							new RandomAccessFile("userData.txt","rw").setLength(0);  //clear the content
							Global.fileUsers.seek(0);
							Global.fileUsers.writeBytes(result);

							//deleting user dialog box
							ImageIcon addIcon1 = new ImageIcon("icons/removeuser.png");
							Image image = addIcon1.getImage(); // transform it 
							Image newimg = image.getScaledInstance(40, 40,  java.awt.Image.SCALE_SMOOTH);
							addIcon1 = new ImageIcon(newimg);  // transform it back
							JOptionPane.showMessageDialog(new JFrame(), "ID : "+idTextField.getText()+"\nRestart required", "Deleted", JOptionPane.INFORMATION_MESSAGE, addIcon1);
							
							nameTextField.setText("");	//clear name field
							idTextField.setText("");	//clear id field
						}
						else
						{
							JOptionPane.showMessageDialog(new JFrame(), "No user with \nID : "+idTextField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
						}
					}
					catch(Exception e)
					{
						JOptionPane.showMessageDialog(new JFrame(), e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
					}
				}
				else
				{
					JOptionPane.showMessageDialog(new JFrame(), "Enter valid ID..!", "Error", JOptionPane.ERROR_MESSAGE); 
				}
			}
		});
		
		showIDButton.addActionListener( new ActionListener() 
		{	
			@Override
			public void actionPerformed(ActionEvent ae)
			{
				if(nameTextField.getDocument().getLength() == 0)
				{
					JOptionPane.showMessageDialog(new JFrame(), "Name cannot be blank..!", "Error", JOptionPane.ERROR_MESSAGE); 
				}
				else
				{
					try
					{
						Global.fileUsers.seek(0);
						int i=0;
						String s;
						while((s=Global.fileUsers.readLine()) != null)
						{
							if(s.equals(nameTextField.getText()))
							{
								i++;
								JOptionPane.showMessageDialog(new JFrame(), "ID = "+Global.fileUsers.readLine());
								break;
							}
							Global.fileUsers.readLine();
						}
						if(i == 0)
						{
							JOptionPane.showMessageDialog(new JFrame(), "No user with name " + nameTextField.getText(), "Error", JOptionPane.ERROR_MESSAGE); 
						}
					}
					catch(Exception e)
					{
						JOptionPane.showMessageDialog(new JFrame(), e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
					}
				}
			}
		});
		jfrm2.setVisible(true);
	}
}

//Setting Frame
class Setting
{
	Setting()
	{	
		ImageIcon logo1 = new ImageIcon("icons/settings.png");
		JFrame jfrm1 = new JFrame("Settings");
		jfrm1.setIconImage(logo1.getImage());  //frame icon
		jfrm1.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		jfrm1.setSize(1000,500);
		jfrm1.setExtendedState( jfrm1.getExtendedState()|JFrame.MAXIMIZED_BOTH );

		JTabbedPane jtp = new JTabbedPane();
		jtp.addTab("Port", new PortSetting());
		jtp.addTab("Help", new HelpSetting());
		jfrm1.add(jtp);
		
		jfrm1.setVisible(true);
	}
}

//Setting port class
class PortSetting extends JPanel
{
	PortSetting()
	{
		setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		
		//Baud Rate

		JLabel textBRLabel = new JLabel("Baud Rate ");
		c.gridx = 0;
		c.gridy = 0;
		add(textBRLabel ,c);
		
		//radio button for fixed baud rate
		JRadioButton fixedBR = new JRadioButton();
		c.gridx = 1;
		c.gridy = 0;
		add(fixedBR , c);
		
		//combo box for standard baud rate values
		String standardBaudRates[] = {"75","110", "300", "600", "1200", "2400", "4800", "9600", "14400", "19200", "38400", "57600", "115200", "128000", "256000"};
		JComboBox<String> fixedBRComboBox = new JComboBox<String>(standardBaudRates);
		c.gridx = 2;
		c.gridy = 0;
		add(fixedBRComboBox , c);

		//radio button for variable baud rate
		JRadioButton variableBR = new JRadioButton("",true);  //select it
		c.gridx = 3;
		c.gridy = 0;
		add(variableBR , c);
		
		//groupin Radio button for selecting only one option
		ButtonGroup bg = new ButtonGroup(); //Groups radio button
		bg.add(fixedBR);
		bg.add(variableBR);
		
		//text box to enter custom baud rate 
		JTextField customBR = new JTextField(6);  //6 columns(characters) visible space
		c.gridx = 4;
		c.gridy = 0;
		customBR.setText(Global.settingFileInfo[0]);
		add(customBR , c);

		//current Baud rate 
		JLabel currentBR = new JLabel("Present Baud Rate = "+Global.settingFileInfo[0]);
		c.gridx = 5;
		c.gridy = 0;
		add(currentBR ,c);

		//ID
		JLabel uniqueIDLabel = new JLabel("ID ");
		c.gridx = 0;
		c.gridy = 1;
		add(uniqueIDLabel ,c);
	
		//ID text field
		JTextField uniqueID = new JTextField(32);  //32 columns(characters) visible space 
		c.gridx = 1;
		c.gridy = 1;
		c.gridwidth = 5;
		uniqueID.setText(Global.settingFileInfo[1]);
		add(uniqueID , c);
		uniqueID.addKeyListener(new KeyAdapter() {	//event for limiting ID
        			@Override
        			public void keyTyped(KeyEvent e) 
			{
            				if (uniqueID.getText().length() > 31  ) // limit to 32 characters (128bit ID (hex) )
			                e.consume();
        			}
    		});

		//save button
		JButton saveButton = new JButton("Save");
		c.gridx = 4;
		c.gridy = 2;
		add(saveButton, c);

		//save button event
		saveButton.addActionListener( new ActionListener() 
		{	@Override
			public void actionPerformed(ActionEvent ae)
			{
				if (  !(customBR.getText().isEmpty()) || (fixedBR.isSelected())  )
				{
					//Text
					String infoToFile= "";
					if(fixedBR.isSelected())
					{
						infoToFile += fixedBRComboBox.getSelectedItem().toString() + "\n";
					}
					if(variableBR.isSelected())
					{
						infoToFile += customBR.getText() + "\n";
					}
					if(uniqueID.getDocument().getLength() == 32)  //length of ID
					{
						infoToFile += uniqueID.getText() + "\n";
						try
						{
													//random acces file do not have buffer so no need of flush
							new RandomAccessFile("lifiSettings.txt","rw").setLength(0);  //clear the content
							Global.fileSettings.seek(0);
							Global.fileSettings.writeBytes(infoToFile);

							ImageIcon addIcon = new ImageIcon("icons/restart.png");
							Image image = addIcon.getImage(); // transform it 
							Image newimg = image.getScaledInstance(40, 40,  java.awt.Image.SCALE_SMOOTH);
							addIcon = new ImageIcon(newimg);  // transform it back
							JOptionPane.showMessageDialog(new JFrame(), "Restart required to apply changes", "Restart" , JOptionPane.INFORMATION_MESSAGE, addIcon);  //restart message after changing settings
						}
						catch(IOException e)
						{
							JOptionPane.showMessageDialog(new JFrame(), e.toString(), "Error", JOptionPane.ERROR_MESSAGE);   
						}
					}
					else
					{
						JOptionPane.showMessageDialog(new JFrame(), "Enter full ID", "Error", JOptionPane.ERROR_MESSAGE); 
					}
				}
				else
				{
					JOptionPane.showMessageDialog(new JFrame(), "Enter Baud Rate", "Error", JOptionPane.ERROR_MESSAGE); 
				}
			}
		});
	}
}

//setting help class
class HelpSetting extends JPanel
{
	HelpSetting()
	{ 
		setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		Font f = new Font("Verdana", Font.PLAIN, 18);
		//about
		JLabel aboutLabel = new JLabel("<html>This application is based on light technology (Li-Fi)<br /><br />Contact : gmail -> lifimessenger@gmail.com</html>");  //html tags for new line, etc..
		aboutLabel.setFont(f);
		c.gridx = 0;
		c.gridy = 0;
		add(aboutLabel ,c);

		//subject
		JTextArea subject = new JTextArea("--subject--");
		subject.setFont(f);
		JScrollPane scrollPane = new JScrollPane(subject);
		c.weightx = 1;  
		c.weighty = 0.1; 
		c.gridx = 0;
		c.gridy = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		add(scrollPane ,c);

		//body
		JTextArea body = new JTextArea("--body--");
		body.setFont(f);
		JScrollPane scrollPane1 = new JScrollPane(body);
		c.weightx = 1;  
		c.weighty = 0.5; 
		c.gridx = 0;
		c.gridy = 2;
		c.fill = GridBagConstraints.BOTH;
		add(scrollPane1 ,c);

		//send
		JButton aboutGmail = new JButton ("Send (redirect to web browser)");
		c.gridx = 0;
		c.gridy = 3;
		c.weightx = 0;  
		c.weighty = 0;
		c.anchor = GridBagConstraints.LAST_LINE_END; 
		add(aboutGmail ,c);
		
		//clear
		JButton aboutClear = new JButton ("Clear");
		c.gridx = 0;
		c.gridy = 4;
		c.weightx = 0;  
		c.weighty = 0;
		c.anchor = GridBagConstraints.LAST_LINE_END; 
		add(aboutClear ,c);

		//send mail event
		aboutGmail.addActionListener( new ActionListener() 
		{	@Override
			public void actionPerformed(ActionEvent ae)
			{
				 //https://mail.google.com/mail/?view=cm&fs=1&to=lifimessenger@gmail.com&su=SUBJECT&body=BODY
				
				String url = "https://mail.google.com/mail/?view=cm&fs=1&to=lifimessenger@gmail.com&su="+subject.getText()+"&body="+body.getText();
				url = url.replace(" ", "+");  // space encoding in url
				try
				{
					java.awt.Desktop.getDesktop().browse(new URI(url));   //redirect to browser along with url
				}
				catch(Exception e)
				{
					JOptionPane.showMessageDialog(new JFrame(), e.toString(), "Error sending email", JOptionPane.ERROR_MESSAGE);
				}
			}
		});

		//clear field event
		aboutClear.addActionListener( new ActionListener() 
		{	@Override
			public void actionPerformed(ActionEvent ae)
			{
				subject.setText("--subject--");
				body.setText("--body--");
			}
		});

	}
	//background image
	@Override
	public void paintComponent(Graphics g) 
	{
		super.paintComponent(g);
		ImageIcon img = new ImageIcon("icons/lightbulb.png");
		Image image = img.getImage(); 	// transform it 
		Image newimg = image.getScaledInstance(110, 110,  java.awt.Image.SCALE_SMOOTH);  //change it
		img = new ImageIcon(newimg);	// transform it back
	 	g.drawImage(img.getImage(), 0, 0, null);
  	}
}

class LIFI
{
	/*onlineToggleButtonIcon and offlineToggleButtonIcon are declared here because when it is placed in any methods then in event handline we get error like -> " local variables referenced from an inner class must be final or effectively final " */
	ImageIcon onlineToggleButtonIcon = new ImageIcon("icons/on.png"); // load the image
	ImageIcon offlineToggleButtonIcon = new ImageIcon("icons/off.png"); // load the image
	int i = 0, j = 0, count = 0;//count no of lines in user info file
	LIFI()
	{
		//warning to not unplug before closing
		ImageIcon unpluggedIcon = new ImageIcon("icons/unplugged.png");
		Image image = unpluggedIcon.getImage(); // transform it 
		Image newimg = image.getScaledInstance(40, 40,  java.awt.Image.SCALE_SMOOTH);
		unpluggedIcon = new ImageIcon(newimg);  // transform it back
		JOptionPane.showMessageDialog(new JFrame(), "Do not unplug the device before closing.", "Caution", JOptionPane.INFORMATION_MESSAGE, unpluggedIcon);
		
		new Global();  

		ImageIcon logo = new ImageIcon("icons/logo.png");
		JFrame jfrm = new JFrame("Li-Fi Messenger");
		jfrm.setIconImage(logo.getImage());  //set icon
		jfrm.setLayout(new GridBagLayout()); 	
		jfrm.setExtendedState( jfrm.getExtendedState()|JFrame.MAXIMIZED_BOTH ); //maximize
		jfrm.setSize(1000,500);

		//Left and Right Panel
		JPanel panelLeft = new JPanel();
		JPanel panelRight = new JPanel();
		JPanel panelTop = new JPanel();

		//Create a border
		Border panelRightBorder = BorderFactory.createLoweredBevelBorder();
		panelRight.setBorder(panelRightBorder);

		//Create a GridBag	
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;  //fill panel HORIZONTALly and VERTICALly

		//left panel
		c.weightx = 0.2;  //20% (row)
		c.weighty = 1;    //100% (column)
		c.gridx = 0;
		c.gridy = 1;
		JScrollPane scrollPaneLeft = new JScrollPane(panelLeft, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		jfrm.add(scrollPaneLeft,c);
	
		//right panel
		c.weightx = 0.8;  //80% (row)
		c.weighty = 1;    //100% (column)
		c.gridx = 1;
		c.gridy = 1;
		jfrm.add(panelRight,c);

		//top panel
		c.weightx = 1;  
		c.weighty = 0.004;    
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 2;
		jfrm.add(panelTop,c);

		//top panel buttons
		//new user
		ImageIcon newUserButtonIcon = new ImageIcon("icons/add.png"); // load the image
		image = newUserButtonIcon.getImage(); // transform it 
		newimg = image.getScaledInstance(20, 20,  java.awt.Image.SCALE_SMOOTH);
		newUserButtonIcon = new ImageIcon(newimg);  // transform it back
		JButton newUserButton = new JButton("New User", newUserButtonIcon);

		//offline (delare at starting of class)
		image = offlineToggleButtonIcon.getImage(); // transform it 
		newimg = image.getScaledInstance(20, 20,  java.awt.Image.SCALE_SMOOTH);
		offlineToggleButtonIcon = new ImageIcon(newimg);
		offlineToggleButtonIcon = new ImageIcon(newimg);  // transform it back
		JToggleButton offlineToggleButton = new JToggleButton(offlineToggleButtonIcon);

		//online (delare at starting of class)
		image = onlineToggleButtonIcon.getImage(); // transform it 
		newimg = image.getScaledInstance(20, 20,  java.awt.Image.SCALE_SMOOTH);
		onlineToggleButtonIcon = new ImageIcon(newimg);  // transform it back
		
		//settings
		ImageIcon settingButtonIcon = new ImageIcon("icons/settings.png"); // load the image
		image = settingButtonIcon.getImage(); // transform it 
		newimg = image.getScaledInstance(20, 20,  java.awt.Image.SCALE_SMOOTH);
		settingButtonIcon = new ImageIcon(newimg);  // transform it back
		JButton settingButton = new JButton("Settings", settingButtonIcon);

		panelTop.add(newUserButton);
		panelTop.add(offlineToggleButton);
		panelTop.add(settingButton);
		panelTop.setLayout(new GridBagLayout());

		//list of USERS in left panel ( panelLeft )
		
		try
		{
			while(Global.fileUsers.readLine() != null)
			  count++;
			Global.fileUsers.seek(0);  //starting position		
		}
		catch(IOException e)
		{
			JOptionPane.showMessageDialog(new JFrame(),e.toString(),"Error", JOptionPane.ERROR_MESSAGE);
		}

		//create layout for users
		panelLeft.setLayout(new GridBagLayout()); 	
		GridBagConstraints leftGBC = new GridBagConstraints();
		leftGBC.fill = GridBagConstraints.HORIZONTAL; 
		leftGBC.weightx = 1; 
		leftGBC.gridx = 0;

		JToggleButton users[] = new JToggleButton[ count/2 ];
		String userDetails[] = new String[ count ];
		ButtonGroup bg = new ButtonGroup(); 

		for( i = 0 , j = 1 ; i < ( count / 2 ) ; i++ , j++ )
		{
			try
			{
				userDetails[i]=Global.fileUsers.readLine();	//name string
				users[i] = new JToggleButton(userDetails[i]);	//button creation
				userDetails[j]=Global.fileUsers.readLine();	//ID string
				users[i].setActionCommand(userDetails[j]);	//replace string when clicked (internally)
				panelLeft.add(users[i] , leftGBC);		//add buttons to panel
				bg.add(users[i]);				//group buttons
			}
			catch(IOException e)
			{
				JOptionPane.showMessageDialog(new JFrame(),e.toString(),"Error", JOptionPane.ERROR_MESSAGE);
			}
		}

		//new user event
		newUserButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae)
			{
				SwingUtilities.invokeLater(new Runnable() { public void run() { new NewUser(); } } );
			}
		});

		//setting event handling
		settingButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae)
			{
				SwingUtilities.invokeLater(new Runnable() { public void run() { new Setting(); } } );
			}
		});

		//inside right panel 
		panelRight.setLayout(new GridBagLayout());
		GridBagConstraints c1 = new GridBagConstraints();
		c1.fill = GridBagConstraints.BOTH;

		//send text area
		c1.weightx = 1;  
		c1.weighty = 0.05; 
		c1.gridx = 0;
		c1.gridy = 1;
		Global.sendMessageTextArea = new JTextArea();
		Font font = new Font("Arial", Font.BOLD, 20);  //font style
		Global.sendMessageTextArea.setFont(font);
		JScrollPane scrollPane = new JScrollPane(Global.sendMessageTextArea); //JScrollPane is just another container that places scrollbars around your component when its needed and also has its own layout.
		panelRight.add(scrollPane ,c1);

		//share button
		c1.weightx = 0.005;  
		c1.weighty = 0.05; 
		c1.gridx = 1;
		c1.gridy = 1;
		ImageIcon sendIconShare = new ImageIcon("icons/clip.png");
		image = sendIconShare.getImage(); // transform it 
		newimg = image.getScaledInstance(20, 20,  java.awt.Image.SCALE_SMOOTH);
		sendIconShare = new ImageIcon(newimg);  // transform it back
		JButton shareButton = new JButton("",sendIconShare);
		panelRight.add(shareButton ,c1);

		//send button
		c1.weightx = 0.005;  
		c1.weighty = 0.05; 
		c1.gridx = 2;
		c1.gridy = 1;
		ImageIcon sendIcon = new ImageIcon("icons/paperplane.png");
		image = sendIcon.getImage(); // transform it 
		newimg = image.getScaledInstance(20, 20,  java.awt.Image.SCALE_SMOOTH);
		sendIcon = new ImageIcon(newimg);  // transform it back
		JButton sendButton = new JButton("send", sendIcon);
		panelRight.add(sendButton ,c1);

		//receive text area
		c1.weightx = 1;  
		c1.weighty = 1; 
		c1.gridx = 0;
		c1.gridy = 0;
		c1.gridwidth = 3;	//fill 4 rows
		Global.receiveMessageTextArea.setEditable(false); 	//read only
		Font f1 = new Font("Verdana", Font.PLAIN, 18);
		Global.receiveMessageTextArea.setFont(f1);
		JScrollPane scrollPane1 = new JScrollPane(Global.receiveMessageTextArea); //JScrollPane is just another container that places scrollbars around your component when its needed and also has its own layout.
		DefaultCaret caret = (DefaultCaret) Global.receiveMessageTextArea.getCaret();
 		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);  //always scroll down
		panelRight.add(scrollPane1 ,c1);

		jfrm.setVisible(true);
		//jfrm.setResizable(false);  //maximize lock

		if(!Global.settingFileInfo[0].isEmpty())
		{
			try
			{	
				CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(Global.portName);
		     	 	CommPort commPort = portIdentifier.open(this.getClass().getName(),2000);  //name,timeout(ms)
            				if(commPort instanceof SerialPort)
            				{
                				SerialPort serialPort = (SerialPort) commPort;
                				serialPort.setSerialPortParams(Integer.parseInt(Global.settingFileInfo[0]), SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);  //Baud rate,data bits,stop bit,parity
              					Global.i = serialPort.getInputStream();
					Global.o = serialPort.getOutputStream();
					Global.runningBaudRateText = serialPort.getBaudRate();

					new Thread(new SerialReader()).start();
			
					//offlineToggleButton event handling
					offlineToggleButton.addItemListener(new ItemListener() {
					@Override
					public void itemStateChanged(ItemEvent ie)
					{
						try
						{
							if(offlineToggleButton.isSelected())  //online
							{
								Global.offline = true;

								//SEND event
								sendButton.addActionListener(new ActionListener() {
									@Override
									public void actionPerformed(ActionEvent ae)
									{
										if(Global.offline)
										{
											if(Global.currentUserID != "")  
											{
												try
												{            
													if(!Global.sendMessageTextArea.getText().equals(""))
													{
														Global.sendFlag = true;
														boolean parityBoolean = false ;
														Global.msg = Global.sendMessageTextArea.getText();
														Global.msgBytes = Global.msg.getBytes();
                    												
														Calendar calendar = Calendar.getInstance();
														calendar.setTimeInMillis( System.currentTimeMillis() + ((((((Global.msgBytes.length * 2) + 13) * 8)) / Global.runningBaudRateText) * 1500) );
														Global.receiveMessageTextArea.append( "Estimated delivery time : " + Global.formatter.format(calendar.getTime()) +"\n" );

														//send text
														//header
														String textHeader = Global.senderID + Global.currentUserID + "000";
														byte[] textHeaderBytes =  textHeader.getBytes();
														//To get header parity (no of 1's odd parity = 1 , no of 1's even parity = 0)
  														for (byte b : textHeaderBytes)	//each character
 														{
 															for (int i = 0; i < 8; i++)	//8bit ascii
  															{
																//128 = 0b10000000  
																if((b & 128) != 0)
																{
																	parityBoolean = !parityBoolean;   // changes alternatively
																}
																b <<= 1;  //left shift by 1
   															}
  														}
														//send text header
														if(parityBoolean) //odd parity (parity = 1)
														{	
															textHeaderBytes[66] = 49;
															Global.o.write(new byte[]{0,0,1}); //start of header
													 		Global.o.write(textHeaderBytes); 
															Global.o.write(new byte[]{3});   //end of text
														}
														else
														{
															Global.o.write(new byte[]{0,0,1});
													 		Global.o.write(textHeaderBytes);
															Global.o.write(new byte[]{3}); // end of text
														}

														long startTime = System.currentTimeMillis();
														startTime += ( ( (((textHeaderBytes.length+5) * 8) ) / Global.runningBaudRateText ) * (1000) ) + ( 3000 + Global.runningBaudRateText ); 
														while(true)
														{
															if(System.currentTimeMillis() < startTime)
															{
																if(Global.sendTimer)
																  break;
															}
															else
															{
																Global.receiveMessageTextArea.append("Error sending\n");
																Global.o.write(new byte[]{0});
																break;
															}
														}
													}
													else
													{
														JOptionPane.showMessageDialog(new JFrame(), "Enter text");
													}	
	            											}
												catch(Exception e) 
												{
													JOptionPane.showMessageDialog(new JFrame(), e.toString(),"Error Sending", JOptionPane.ERROR_MESSAGE);
												}
												
											}
											else
											{
												JOptionPane.showMessageDialog(new JFrame(),"Select a user or create one to send" ,"No user", JOptionPane.ERROR_MESSAGE);
											}
										}      
									}
								});
								
								//SHARE event
								shareButton.addActionListener(new ActionListener() {
									@Override
									public void actionPerformed(ActionEvent ae)
									{
										if(Global.offline)
										{
											if(Global.currentUserID != "")  
											{
												try
												{
													JFileChooser fileChooser = new JFileChooser();
													fileChooser.setApproveButtonText("Send");
													fileChooser.setApproveButtonToolTipText("Send to recipient");
													fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
													int result = fileChooser.showOpenDialog(null);

													if (result == JFileChooser.APPROVE_OPTION)
													{
														Global.sendFile = fileChooser.getSelectedFile();
														Global.sendFlag = true;
														boolean parityBoolean = false;
    														String fileHeader = Global.senderID + Global.currentUserID + "0100"+Global.sendFile.length()+"/"+Global.sendFile.getName();
														byte[] fileHeaderBytes =  fileHeader.getBytes();
														
														for (byte b : fileHeaderBytes)	//each character
 														{
 															for (int i = 0; i < 8; i++)	//8bit ascii
  															{
																//128 = 0b10000000  
																if((b & 128) != 0)
																{
																	parityBoolean = !parityBoolean;   // changes alternatively
																}
																b <<= 1;  //left shift by 1
   															}
  														}

														Calendar calendar = Calendar.getInstance();
														calendar.setTimeInMillis( System.currentTimeMillis() + (((((Global.sendFile.length() + (fileHeaderBytes.length *2) + 13 ) * 8)) / Global.runningBaudRateText) * 1500) );
														Global.receiveMessageTextArea.append( "Estimated delivery time : " + Global.formatter.format(calendar.getTime()) +"\n" );

														if(parityBoolean) //odd parity (parity = 1) 
														{
															fileHeaderBytes[66] = 49;
															Global.o.write(new byte[]{0,0,1}); //start of header
													 		Global.o.write(fileHeaderBytes); 
															Global.o.write(new byte[]{3}); //end of text
														}
														else
														{
															Global.o.write(new byte[]{0,0,1});
													 		Global.o.write(fileHeaderBytes);
															Global.o.write(new byte[]{3});
														}
														long startTime = System.currentTimeMillis();
														startTime += ( ( (((fileHeaderBytes.length+4) * 8) ) / Global.runningBaudRateText ) * (1000) ) + ( 3000 + Global.runningBaudRateText ); 
														while(true)
														{
															if(System.currentTimeMillis() < startTime)
															{
																if(Global.sendTimer)
																  break;
															}
															else
															{
																Global.receiveMessageTextArea.append("Error sending\n");
																Global.o.write(new byte[]{0});
																break;
															}
														}
													}	
												}
												catch(Exception e) 
												{
													JOptionPane.showMessageDialog(new JFrame(), e.toString(),"Error Sending", JOptionPane.ERROR_MESSAGE);
												}
											}
											else
											{
												JOptionPane.showMessageDialog(new JFrame(),"Select a user or create one to send" ,"No user", JOptionPane.ERROR_MESSAGE);
											}
										}
									}
								});

								offlineToggleButton.setIcon(onlineToggleButtonIcon);  //change icon	
		               				}
		               				else   //offline
							{	
								Global.offline = false;
								offlineToggleButton.setIcon(offlineToggleButtonIcon);		
							}
						}
						catch(Exception e)
						{
							JOptionPane.showMessageDialog(new JFrame() , e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
						}
					}
					});
	               		}
		               	else
		               	{
					JOptionPane.showMessageDialog(new JFrame(), "Only serial ports are handled","Error", JOptionPane.ERROR_MESSAGE);
	               		}			
			}
			catch(Exception e)
			{
				JOptionPane.showMessageDialog(new JFrame(),"plugin the device or already in use \n "+e.toString(),"Error", JOptionPane.ERROR_MESSAGE);
			}	
		}
		else
		{
			JOptionPane.showMessageDialog(new JFrame(),"Go to settings and set Baud Rate as it is first use.","Set speed", JOptionPane.ERROR_MESSAGE);
		}

		//users event
		for( i = 0 ; i < ( count / 2 ) ; i++ )
		{
			users[i].addItemListener(new ItemListener() {
				@Override
				public void itemStateChanged(ItemEvent ie)
				{
					for( j = 0 ; j < ( count / 2 ) ; j++ )
					{
						if(users[j].isSelected()) 
						{
							try
							{
								Global.currentUserID = users[j].getActionCommand() ;  //get selected user ID 
								Global.receiveMessageTextArea.setText("");  //clear receiver text area
								Global.rs = Global.stmt.executeQuery( "select dataDB from `"+ users[j].getActionCommand() +"`;" );  //select dataDB from ___ID___;
								while(Global.rs.next())  //It is used to move the cursor to the one row next from the current position.
								{
									Global.receiveMessageTextArea.append(Global.rs.getString(1)+"\n");	//It is used to return the data of specified column index of the current row as String.
								}
							}
							catch(Exception e)
							{
								JOptionPane.showMessageDialog(new JFrame(),e.toString(),"Error", JOptionPane.ERROR_MESSAGE);
							}
						}
					}
				}
			});
		}

		//closing
		jfrm.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		jfrm.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) 
			{
				try
				{	
					//close operations are done by default
					System.exit(0);	
				}
				catch(Exception e)
				{
					e.printStackTrace();
					JOptionPane.showMessageDialog(new JFrame(), "Closing Error \n "+e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
		});
	
	}

	public static void main(String args[])
	{
		SwingUtilities.invokeLater(new Runnable() { public void run() { new PortSelect(); } } ); 
	}
}
