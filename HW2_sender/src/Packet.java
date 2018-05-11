import java.io.Serializable;
import java.io.UnsupportedEncodingException;



public class Packet implements Serializable{

	short cksum; // 16-bit 2-byte

	short len; // 16-bit 2-byte

	int ackno; // 32-bit 4-byte

	int seqno; // 32-bit 4-byte Data packet Only

	byte[] data; // 0-500 bytes. Data packet only. Variable
	
	long sentTime;// time this was sent
	
	
	//constructor 
	public Packet(short cksum, short len, int ackno, byte[] data, int seqno){
		this.cksum = cksum;
		this.ackno = ackno;
		this.len = len;
		this.seqno = seqno;
		this.data = data;
		
	}
	
	public void ToString(){
		System.out.print("Seqno: "+seqno);
		System.out.print(" ackno: "+ackno);
		
		//System.out.println(cksum);
		//System.out.println(len);
		
		
		//try {
			//System.out.println(new String(data, "UTF-8"));
		//} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
		//	System.out.println("error occured while extracting sent data");
		//}
	}

}
