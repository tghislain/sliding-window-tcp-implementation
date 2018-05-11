import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Sender {
	// packet information
	static int size;
	static int windowSize;
	static int timeout;
	static Float drop;
	// receiver information
	static String receiverIP;
	static int receiverPORT;

	public static void main(String[] args) throws IOException {
		size = Integer.parseInt(args[1]);
		timeout = Integer.parseInt(args[3]);
		windowSize = Integer.parseInt(args[5]);
		drop = Float.valueOf(args[7]);
		receiverIP = args[8];
		receiverPORT = Integer.parseInt(args[9]);

		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		AtomicInteger sequence = new AtomicInteger();

		int offset = 0;

		// queue for the window
		Queue<Packet> window = new ConcurrentLinkedDeque<Packet>();
		byte[] dataBytes = new byte[500];

		File file = new File("input");
		final FileInputStream input = new FileInputStream(file);

		// open socket to be used
		try {

			// thread to listen for acks
			Thread listener = new Thread() {
				public void run() {
					System.out.println("listening for acks");
					try {
						while (true) {

							try (DatagramSocket socket = new DatagramSocket(6000)) {
								byte[] inputBuffer = new byte[5000];
								DatagramPacket response = new DatagramPacket(inputBuffer, 5000);
								socket.receive(response);

								ByteArrayInputStream byteStream = new ByteArrayInputStream(inputBuffer);
								ObjectInputStream is = new ObjectInputStream(new BufferedInputStream(byteStream));
								try {
									Packet temp = (Packet) is.readObject();
									// check if this is an ack packet
									if (temp.len == 8) {
										// remove the pack with this ackno
										// and
										// send another packet
										boolean found = false;
										boolean movewnd = false;
										for (Packet p : window) {
											// check if we are waiting on this
											// packet or if the acknowledgemet
											// is not corrupted

											if (p.seqno == temp.ackno && temp.cksum != 0) {

												// if window was full print move
												// window
												if (window.size() == windowSize) {
													movewnd = true;

												}

												window.remove(p);
												found = true;

												// send new packet and add
												// it to
												// the window
												int readBytes = input.read(dataBytes);
												if (readBytes != -1 && window.size() < windowSize) {
													short cksum = 1;
													short len = (short) readBytes;
													int ackno = -1;
													int seqno = sequence.getAndIncrement();
													Packet temp2 = new Packet(cksum, len, ackno, dataBytes, seqno);
													temp2.sentTime = System.nanoTime();
													
													Random rn = new Random();
													int decision = 0;
													if (drop > 0) {
														decision = rn.nextInt(3 - 1 + 1);
													} else {
														decision = rn.nextInt(2 - 1 + 1);
													}
													temp2.cksum = (short) decision;

													// socket.setSoTimeout(100000);
													InetAddress host = InetAddress.getByName(receiverIP);

													ByteArrayOutputStream bStream = new ByteArrayOutputStream();
													ObjectOutputStream oStream = new ObjectOutputStream(bStream);
													oStream.flush();
													oStream.writeObject(temp2);
													oStream.flush();

													byte[] packetObjectBytes = bStream.toByteArray();
													DatagramPacket request = new DatagramPacket(packetObjectBytes,
															packetObjectBytes.length, host, receiverPORT);
													// DatagramPacket
													// response =
													// new
													// DatagramPacket(new
													// byte[1000], 1000);
													//Thread.sleep(1000);
													synchronized (System.out) {
														window.add(temp2);
														System.out.print("SENDING ");
														temp2.ToString();

														switch (decision) {
														case 1:
															if (movewnd) {
																System.out.print(" SENT");
																System.out.println(" MOVEWND");
																movewnd = false;
															} else {
																System.out.println(" SENT");
																socket.send(request);
															}
															break;
														case 0:
															if (movewnd) {
																System.out.print(" ERR");
																System.out.println(" MOVEWND");
																movewnd = false;
															} else {
																System.out.println(" ERR");
																socket.send(request);
															}
															break;
														case 2:
															if (movewnd) {
																System.out.print(" DROP");
																System.out.println(" MOVEWND");
																movewnd = false;
															} else
																System.out.println(" DROP");
															break;

														}
													}

												}
											}

										}
										synchronized (System.out) {
											System.out.print("ACKRCVD ");
											temp.ToString();
											if (found && temp.cksum != 0) {
												System.out.println();
											} else {
												// no packet waiting for
												// this
												// ack. or ack errored
												if (temp.cksum == 0) {
													System.out.println(" ERRACK");
												} else if (!found) {
													System.out.println(" DUPLACK");
												}
											}

										}

									}
								} catch (ClassNotFoundException e) {

									System.out.println("error occured while reading received ack object 1");
									e.printStackTrace();
								}
								// is.close();

							} catch (IOException | RuntimeException ex) {
								System.out.println("error occured while reading received ack object 2");
								ex.printStackTrace();
							}
						}

					} catch (Exception v) {
						System.out.println("error occured while reading received ack object 4");
						v.printStackTrace();
					}
				}
			};
			// listener.run();

			// thread to check timeouts
			Thread timeouts = new Thread() {
				public void run() {
					System.out.println("monitoring timeouts");
					try {
						try (DatagramSocket socket = new DatagramSocket(0)) {

							while (true) {

								// System.out.println(window.size() +" size");
								for (Packet p : window) {
									// System.out.println(System.nanoTime() -
									// p.sentTime + " timeleft for " + p.seqno);
									// System.out.println(System.nanoTime() );
									// System.out.println(p.sentTime);
									// System.out.println(System.nanoTime() -
									// p.sentTime);
									// System.out.println(System.nanoTime());
									// System.out.println(p.sentTime);

									if (((System.nanoTime() - p.sentTime)) > (timeout * 1000000000)) {
										// re send p

										synchronized (System.out) {
											System.out.print("TIMED-OUT ");
											p.ToString();
											System.out.println();
										}

										Random rn = new Random();
										int decision = 0;
										if (drop > 0) {
											decision = rn.nextInt(3 - 1 + 1);
										} else {
											decision = rn.nextInt(2 - 1 + 1);
										}
										p.cksum = (short) decision;
										p.sentTime = System.nanoTime();

										// socket.setSoTimeout(100000);
										InetAddress host = InetAddress.getByName(receiverIP);
										ByteArrayOutputStream bStream = new ByteArrayOutputStream();
										ObjectOutputStream oStream = new ObjectOutputStream(bStream);
										oStream.flush();
										oStream.writeObject(p);
										oStream.flush();

										byte[] packetObjectBytes = bStream.toByteArray();

										DatagramPacket request = new DatagramPacket(packetObjectBytes,
												packetObjectBytes.length, host, receiverPORT);
										// DatagramPacket response = new
										// DatagramPacket(new byte[1000],
										// 1000);
										Thread.sleep(1000);
										synchronized (System.out) {
											System.out.print("RE-SENDING ");
											p.ToString();
											switch (decision) {
											case 1:
												System.out.println(" SENT");
												socket.send(request);
												break;
											case 0:
												System.out.println(" ERR");
												socket.send(request);
												break;
											case 2:
												System.out.println(" DROP");
												break;

											}

										}
									}
								}
							}
						} catch (Exception ex) {
							System.out.println("something went bad checking timeouts1");
							ex.printStackTrace();
						}
					} catch (Exception v) {
						System.out.println("something went bad checking timeouts2");
						v.printStackTrace();
					}
				}
			};
			// timeouts.run();

			// run the listener and timeouts checker
			ExecutorService executor = Executors.newFixedThreadPool(2);
			executor.submit(timeouts);
			executor.submit(listener);

			// send the first x packets to fill the window
			int readBytes = input.read(dataBytes);
			while (readBytes != -1 && window.size() < windowSize) {
				short cksum = 1;
				short len = (short) readBytes;
				int ackno = -1;
				int seqno = sequence.getAndIncrement();
				Packet temp = new Packet(cksum, len, ackno, dataBytes, seqno);
				temp.sentTime = System.nanoTime();
				// get a random number to decide if we send, drop or corrupt the
				// packet
				Random rn = new Random();
				int decision = 0;
				if (drop > 0) {
					decision = rn.nextInt(3 - 1 + 1);
				} else {
					decision = rn.nextInt(2 - 1 + 1);
				}
				temp.cksum = (short) decision;
					
				
				window.add(temp);

				try (DatagramSocket socket = new DatagramSocket(0)) {

					// socket.setSoTimeout(100000);
					InetAddress host = InetAddress.getByName(receiverIP);

					ByteArrayOutputStream bStream = new ByteArrayOutputStream();
					ObjectOutputStream oStream = new ObjectOutputStream(bStream);
					oStream.flush();
					oStream.writeObject(temp);
					oStream.flush();

					byte[] packetObjectBytes = bStream.toByteArray();

					DatagramPacket request = new DatagramPacket(packetObjectBytes, packetObjectBytes.length, host,
							receiverPORT);
					// DatagramPacket response = new DatagramPacket(new
					// byte[1000],
					// 1000);

					Thread.sleep(1000);
					synchronized (System.out) {
						System.out.print("SENDING ");
						temp.ToString();

						switch (decision) {
						case 1:
							System.out.println(" SENT");
							socket.send(request);
							break;
						case 0:
							System.out.println(" ERR");
							socket.send(request);
							break;
						case 2:
							System.out.println(" DROP");
							break;

						}
					}

					// socket.receive(response);
					// read next bytes
					readBytes = input.read(dataBytes);

				} catch (IOException ex) {
					System.out.println("something went bad sending the first window");
					ex.printStackTrace();
				}

			}
			// System.out.println("sent first window");
		} catch (Exception e) {
			System.out.println("something went bad sending the socket");
			e.printStackTrace();
		}

	}

}
