"""Exercise the actual bundled FastCGI library over TCP, without Apache."""
import socket,struct,subprocess,time,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
with socket.socket() as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
p=subprocess.Popen(['java',f'-DFCGI_PORT={port}','-jar','httpd-root/fcgi-bin/app.jar'],cwd=root)
def record(kind,data):return struct.pack('!BBHHBB',1,kind,1,len(data),0,0)+data
def req(body,cookie='',method='POST'):
 with socket.create_connection(('127.0.0.1',port),timeout=3) as s:
  params={'REQUEST_METHOD':method,'CONTENT_TYPE':'application/x-www-form-urlencoded','CONTENT_LENGTH':str(len(body)),'HTTP_COOKIE':cookie}
  pairs=b''.join(bytes([len(k),len(v)])+k.encode()+v.encode() for k,v in params.items())
  s.sendall(record(1,struct.pack('!HB5x',1,0))+record(4,pairs)+record(4,b'')+record(5,body.encode())+record(5,b''))
  output=b''
  def exact(n):
   out=b''
   while len(out)<n:
    part=s.recv(n-len(out))
    if not part:raise RuntimeError('Unexpected EOF')
    out+=part
   return out
  while True:
   _,kind,_,n,padding,_=struct.unpack('!BBHHBB',exact(8));data=exact(n);exact(padding)
   if kind==6:output+=data
   if kind==3:break
  headers,raw=output.decode().split('\r\n\r\n',1)
  return headers,json.loads(raw)
try:
 for _ in range(100):
  try:
   h,d=req('action=history');break
  except ConnectionRefusedError:time.sleep(.05)
 else:raise RuntimeError('Server did not start')
 cookie=next(l.split(': ',1)[1].split(';')[0] for l in h.splitlines() if l.startswith('Set-Cookie:'))
 h,d=req('action=check&x=-1&y=-1&r=2&r=3',cookie);assert len(d['results'])==2 and all(i['hit'] for i in d['results'])
 assert len(req('action=history',cookie)[1]['results'])==2
 assert not req('action=history')[1]['results']
 assert '400' in req('action=check&x=0&y=3&r=1',cookie)[0]
 assert '405' in req('action=history',cookie,'GET')[0]
 assert not req('action=clear',cookie)[1]['results']
 print('FastCGI POST, history, isolation, clear, 400 and 405 passed')
finally:p.terminate();p.wait()
