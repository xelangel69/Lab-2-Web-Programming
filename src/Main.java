import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;

public class Main {
    static final int LIMIT=65536;
    static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    static boolean hit(BigDecimal x, BigDecimal y, BigDecimal r){
        BigDecimal z=BigDecimal.ZERO,two=new BigDecimal(2),four=new BigDecimal(4);
        return x.multiply(two).compareTo(r.negate())>=0 && x.signum()<=0 && y.signum()>=0 && y.compareTo(r)<=0
                || x.compareTo(r.negate())>=0 && x.signum()<=0 && y.signum()<=0 && y.multiply(two).compareTo(x.negate().subtract(r))>=0
                || x.signum()>=0 && y.signum()<=0 && x.multiply(x).add(y.multiply(y)).multiply(four).compareTo(r.multiply(r))<=0;
    }
    static String process(String body,long start){
        List<Map<String,String>> batch = new JsonBatch(body).parse();
        List<String> rows=new ArrayList<>();
        for (Map<String,String> item : batch) {
            if (!item.keySet().equals(Set.of("x", "y", "r"))) throw new IllegalArgumentException();
            String xs=item.get("x"), ys=item.get("y"), rs=item.get("r");
            if (!xs.matches("-?(0|[1-9][0-9]*)") || !rs.matches("-?(0|[1-9][0-9]*)")
                    || ys.length()>100 || !ys.matches("-?[0-9]+(\\.[0-9]+)?")) throw new IllegalArgumentException();
            BigDecimal x=new BigDecimal(xs), y=new BigDecimal(ys), r=new BigDecimal(rs);
            if(x.abs().compareTo(new BigDecimal(4))>0||y.abs().compareTo(new BigDecimal(5))>0||r.compareTo(BigDecimal.ONE)<0||r.compareTo(new BigDecimal(5))>0)throw new IllegalArgumentException();
            rows.add("{\"x\":"+x+",\"y\":\""+y.toPlainString()+"\",\"r\":"+r+",\"hit\":"+hit(x,y,r)+",\"timestamp\":\""+Instant.now()+"\"}");
        }
        if(rows.isEmpty())throw new IllegalArgumentException();
        return "{\"results\":["+String.join(",",rows)+"],\"durationMs\":"+((System.nanoTime()-start)/1e6)+"}";
    }
    /** Strict JSON subset for a batch of flat coordinate objects; numbers or strings. */
    static final class JsonBatch {
        final String text; int pos;
        JsonBatch(String text) { this.text=text; }
        void ws() { while(pos<text.length() && " \t\r\n".indexOf(text.charAt(pos))>=0) pos++; }
        boolean take(char c) { ws(); if(pos<text.length() && text.charAt(pos)==c){pos++;return true;} return false; }
        void expect(char c) { if(!take(c)) throw new IllegalArgumentException("Invalid JSON"); }
        String string() {
            expect('"'); StringBuilder out=new StringBuilder();
            while(pos<text.length()) {
                char c=text.charAt(pos++);
                if(c=='"') return out.toString();
                if(c<32) throw new IllegalArgumentException();
                if(c=='\\') {
                    if(pos==text.length()) throw new IllegalArgumentException();
                    c=text.charAt(pos++);
                    switch(c) {
                        case '"', '\\', '/' -> out.append(c);
                        case 'b' -> out.append('\b'); case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                        case 'u' -> {
                            if(pos+4>text.length()) throw new IllegalArgumentException();
                            String hex=text.substring(pos,pos+4);
                            if(!hex.matches("[0-9a-fA-F]{4}")) throw new IllegalArgumentException();
                            out.append((char)Integer.parseInt(hex,16));pos+=4;
                        }
                        default -> throw new IllegalArgumentException();
                    }
                } else out.append(c);
            }
            throw new IllegalArgumentException();
        }
        String value() {
            ws(); if(pos<text.length() && text.charAt(pos)=='"') return string();
            Matcher m=Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?").matcher(text);
            m.region(pos,text.length()); if(!m.lookingAt()) throw new IllegalArgumentException();
            pos=m.end(); return m.group();
        }
        List<Map<String,String>> parse() {
            List<Map<String,String>> batch=new ArrayList<>(); expect('[');
            if(!take(']')) {
                do {
                    if(batch.size()==5) throw new IllegalArgumentException();
                    expect('{'); Map<String,String> item=new HashMap<>();
                    if(!take('}')) {
                        do { String key=string(); expect(':'); String value=value();
                            if(item.putIfAbsent(key,value)!=null) throw new IllegalArgumentException();
                        } while(take(','));
                        expect('}');
                    }
                    batch.add(item);
                } while(take(','));
                expect(']');
            }
            ws(); if(pos!=text.length()) throw new IllegalArgumentException(); return batch;
        }
    }
    static int length(DataInputStream in)throws IOException{int b=in.readUnsignedByte();return (b&128)==0?b:((b&127)<<24)|(in.readUnsignedByte()<<16)|(in.readUnsignedByte()<<8)|in.readUnsignedByte();}
    static void record(OutputStream out,int type,int id,byte[] data)throws IOException{
        for(int pos=0;pos<data.length;){int n=Math.min(65535,data.length-pos);out.write(new byte[]{1,(byte)type,(byte)(id>>8),(byte)id,(byte)(n>>8),(byte)n,0,0});out.write(data,pos,n);pos+=n;}
        if(data.length==0)out.write(new byte[]{1,(byte)type,(byte)(id>>8),(byte)id,0,0,0,0});
    }
    static void serve(Socket socket)throws IOException{
        socket.setSoTimeout(10000);DataInputStream in=new DataInputStream(socket.getInputStream());OutputStream out=socket.getOutputStream();
        ByteArrayOutputStream params=new ByteArrayOutputStream(),body=new ByteArrayOutputStream();int id=0;boolean oversized=false;
        while(true){int v=in.readUnsignedByte(),type=in.readUnsignedByte();id=in.readUnsignedShort();int n=in.readUnsignedShort(),pad=in.readUnsignedByte();in.readUnsignedByte();if(v!=1||id==0)throw new IOException("Unsupported FastCGI record");byte[] data=in.readNBytes(n);if(data.length!=n)throw new EOFException();in.skipNBytes(pad);
            if(type==4){if(params.size()+n>LIMIT)throw new IOException("Parameters too large");params.write(data);}
            if(type==5){if(body.size()+n>LIMIT)oversized=true;else body.write(data);if(n==0)break;}
        }
        Map<String,String> env=new HashMap<>();DataInputStream p=new DataInputStream(new ByteArrayInputStream(params.toByteArray()));while(p.available()>0){int a=length(p),b=length(p);if(a<0||b<0||a+b>p.available())throw new IOException("Invalid parameters");env.put(new String(p.readNBytes(a),StandardCharsets.UTF_8),new String(p.readNBytes(b),StandardCharsets.UTF_8));}
        long start=System.nanoTime();String status="200 OK",json;
        if(oversized){status="413 Content Too Large";json="{\"error\":\"Request too large\"}";}
        else if(!"POST".equals(env.get("REQUEST_METHOD"))){status="405 Method Not Allowed";json="{\"error\":\"Use POST\"}";}
        else if(!env.getOrDefault("CONTENT_TYPE","").split(";",2)[0].trim().equalsIgnoreCase("application/json")){status="415 Unsupported Media Type";json="{\"error\":\"Use application/json\"}";}
        else try{json=process(body.toString(StandardCharsets.UTF_8),start);}catch(RuntimeException e){status="400 Bad Request";json="{\"error\":\"Invalid batch: expected [{x,y,r}] with x integer -4..4, y -5..5, r integer 1..5\"}";}
        byte[] payload=bytes(json);record(out,6,id,bytes("Status: "+status+"\r\nContent-Type: application/json; charset=UTF-8\r\nCache-Control: no-store\r\nAllow: POST\r\nContent-Length: "+payload.length+"\r\n\r\n"+json));record(out,6,id,new byte[0]);record(out,3,id,new byte[8]);out.flush();
    }
    public static void main(String[] args)throws Exception{int port=args.length==0?9000:Integer.parseInt(args[0]);try(ServerSocket server=new ServerSocket(port,50,InetAddress.getLoopbackAddress())){System.err.println("FastCGI listening on "+port);while(true)try(Socket socket=server.accept()){serve(socket);}catch(IOException e){System.err.println(e.getMessage());}}}
}
