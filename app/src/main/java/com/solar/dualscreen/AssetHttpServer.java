package com.solar.dualscreen;

import android.content.res.AssetManager;
import java.io.*;
import java.net.*;
import java.util.*;

public class AssetHttpServer extends Thread {
    private final int port;
    private final AssetManager assets;
    private volatile boolean running = true;
    
    public AssetHttpServer(int port, AssetManager assets) {
        this.port = port;
        this.assets = assets;
        setDaemon(true);
    }
    
    public void stopServer() { running = false; }
    
    @Override
    public void run() {
        try (ServerSocket ss = new ServerSocket(port, 10, InetAddress.getByName("127.0.0.1"))) {
            while (running) {
                try { new ClientHandler(ss.accept(), assets).start(); }
                catch (IOException e) { if (running) e.printStackTrace(); }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }
    
    static class ClientHandler extends Thread {
        private final Socket socket;
        private final AssetManager assets;
        
        ClientHandler(Socket s, AssetManager a) { socket = s; assets = a; setDaemon(true); }
        
        @Override
        public void run() {
            try {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line = reader.readLine();
                if (line == null) { socket.close(); return; }
                String[] parts = line.split(" ");
                String path = parts.length > 1 ? parts[1] : "/";
                if (path.equals("/")) path = "/planet.html";
                if (path.startsWith("/")) path = path.substring(1);
                
                String mime = "text/plain";
                if (path.endsWith(".html")) mime = "text/html";
                else if (path.endsWith(".js")) mime = "application/javascript";
                else if (path.endsWith(".css")) mime = "text/css";
                else if (path.endsWith(".png")) mime = "image/png";
                else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) mime = "image/jpeg";
                else if (path.endsWith(".glb")) mime = "application/octet-stream";
                
                try {
                    InputStream assetStream = assets.open(path);
                    byte[] data = readAll(assetStream);
                    assetStream.close();
                    sendResponse(out, 200, "OK", mime, data);
                } catch (IOException e) {
                    sendResponse(out, 404, "Not Found", "text/plain", "404".getBytes());
                }
                socket.close();
            } catch (IOException e) { /* client disconnect */ }
        }
        
        private byte[] readAll(InputStream in) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
        
        private void sendResponse(OutputStream out, int code, String msg, String mime, byte[] data) throws IOException {
            String header = "HTTP/1.0 " + code + " " + msg + "\r\n" +
                "Content-Type: " + mime + "\r\n" +
                "Content-Length: " + data.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n";
            out.write(header.getBytes());
            out.write(data);
            out.flush();
        }
    }
}
