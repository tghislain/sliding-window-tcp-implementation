import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;

public class Receiver {

	// packet information
	static int window;
	static Float drop;
	// receiver information
	static String receiverIP;
	static int receiverPORT;

	public static void main(String[] args) {

		window = Integer.parseInt(args[1]);
		drop = Float.valueOf(args[3]);
		receiverIP = args[4];
		receiverPORT = Integer.parseInt(args[5]);

		Queue<Packet> window = new ConcurrentLinkedDeque<Packet>();
		int seq = 0;

		File file = new File("output");
		FileOutputStream output = null;
		try {
			output = new FileOutputStream(file);
		} catch (FileNotFoundException e1) {

			e1.printStackTrace();
		}

		try (DatagramSocket socket = new DatagramSocket(5000)) {
			System.out.println("listening for packets");
			while (true) {

				Packet temp = null;
				try {
					byte[] inputBuffer = new byte[5000];
					DatagramPacket request = new DatagramPacket(inputBuffer, 5000);
					// socket.setSoTimeout(300000);
					socket.receive(request);
					boolean errored = false;

					ByteArrayInputStream byteStream = new ByteArrayInputStream(inputBuffer);
					ObjectInputStream is = new ObjectInputStream(new BufferedInputStream(byteStream));

					try {
						temp = (Packet) is.readObject();
						// print received packet if it is not an error
						if (temp.cksum != 0) {
							// save received packet in the window

							if (temp.seqno == seq) {
								// write the contents
								output.write(temp.data);
								seq++;
							} else {
								window.add(temp);
								for (Packet p : window) {
									if (p.seqno == seq) {
										output.write(temp.data);
										seq++;
									}
								}
							}

							System.out.print("RECEIVING ");
							temp.ToString();
						} else
							errored = true;

					} catch (ClassNotFoundException e) {

						System.out.println("error occured while reading received data object");
					}
					Random rn = new Random();
					int decision = 0;
					if (drop > 0) {
						decision = rn.nextInt(3 - 1 + 1);
					} else {
						decision = rn.nextInt(2 - 1 + 1);
					}

					// is.close();
					// set length to 8 to make it an ack packet
					temp.cksum = (short) decision;
					temp.len = 8;
					temp.ackno = temp.seqno;
					ByteArrayOutputStream bStream = new ByteArrayOutputStream();
					ObjectOutputStream oStream = new ObjectOutputStream(bStream);
					oStream.flush();
					oStream.writeObject(temp);
					oStream.flush();

					// socket.setSoTimeout(10000);
					InetAddress host = InetAddress.getByName(receiverIP);

					byte[] packetObjectBytes = bStream.toByteArray();

					DatagramPacket response = new DatagramPacket(packetObjectBytes, packetObjectBytes.length, host,
							receiverPORT);

					// send acknowledgement if packet was not an error

					if (!errored) {
						switch (decision) {
						case 1:
							System.out.println(" SENT");
							socket.send(response);
							break;
						case 0:
							System.out.println(" ERR");
							socket.send(response);
							break;
						case 2:
							System.out.println(" DROP");
							break;

						}
					}

				} catch (IOException | RuntimeException ex) {
					ex.printStackTrace();
				}
			}
		} catch (IOException ex) {
			System.out.println("something went bad with the socket");
		}
	}

}
