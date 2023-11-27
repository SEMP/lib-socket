package py.com.semp.lib.socket;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketServerEjemplo
{
	private ServerSocket serverSocket;
	
	public void start(int port)
	{
		try
		{
			serverSocket = new ServerSocket(port);
			while(true)
			{
				new ClientHandler(serverSocket.accept()).start();
			}
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
	
	private static class ClientHandler extends Thread
	{
		private Socket clientSocket;
		
		public ClientHandler(Socket socket)
		{
			this.clientSocket = socket;
		}
		
		public void run()
		{
			try(BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true))
			{
				
				String inputLine;
				while((inputLine = in.readLine()) != null)
				{
					// Process the input and send response
					out.println("Echo: " + inputLine);
				}
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
			finally
			{
				try
				{
					clientSocket.close();
				}
				catch(IOException e)
				{
					e.printStackTrace();
				}
			}
		}
	}
	
	public void stop() throws IOException
	{
		serverSocket.close();
	}
	
	public static void main(String[] args)
	{
		SocketServerEjemplo server = new SocketServerEjemplo();
		server.start(8080);
	}
}
