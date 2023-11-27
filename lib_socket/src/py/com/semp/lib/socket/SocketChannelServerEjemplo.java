package py.com.semp.lib.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;

public class SocketChannelServerEjemplo
{
	public static void main(String[] args) throws IOException
	{
		int port = 8080;
		ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		serverSocketChannel.configureBlocking(false);
		
		Selector selector = Selector.open();
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		
		while(true)
		{
			if(selector.select() == 0)
			{
				continue;
			}
			
			for(SelectionKey key : selector.selectedKeys())
			{
				if(key.isAcceptable())
				{
					SocketChannel client = serverSocketChannel.accept();
					client.configureBlocking(false);
					client.register(selector, SelectionKey.OP_READ);
				}
				else if(key.isReadable())
				{
					SocketChannel client = (SocketChannel)key.channel();
					ByteBuffer buffer = ByteBuffer.allocate(256);
					client.read(buffer);
					String message = new String(buffer.array()).trim();
					System.out.println("Message received: " + message);
					// Handle message
				}
				// Remove the key to prevent the same key from being processed again
				selector.selectedKeys().remove(key);
			}
		}
	}
}
