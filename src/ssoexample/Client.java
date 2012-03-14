package ssoexample;

import com.sun.jna.platform.win32.Sspi;
import com.sun.jna.platform.win32.Win32Exception;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.jboss.netty.handler.codec.serialization.ObjectEncoder;
import waffle.windows.auth.IWindowsSecurityContext;
import waffle.windows.auth.impl.WindowsSecurityContextImpl;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Client {
    private ChannelFuture channelFuture;
    private IWindowsSecurityContext clientContext;
    private final Logger logger = Logger.getLogger(getClass());
    private CommandLine cmd;

    public Client(String[] args) {
        try {
            Options options = new Options();
            options.addOption("n", true, "server hostname");
            options.addOption("p", true, "server port");
            options.addOption("s", true, "SPN");
            options.addOption("h", false, "print this message");

            cmd = new PosixParser().parse(options, args);

            if (cmd.hasOption("h") || !(cmd.hasOption("n") && cmd.hasOption("p"))) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("client", options);
                System.exit(0);
            }

            final SimpleChannelUpstreamHandler handler = new SimpleChannelUpstreamHandler() {
                @Override
                public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
                    e.getChannel().write(new HelloMessage());
                }

                @Override
                public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) {
                    logger.info("message received: " + event.getMessage());
                    if (event.getMessage() instanceof AuthorizationRequiredMessage) {
                        clientContext = WindowsSecurityContextImpl.getCurrent("Negotiate", cmd.getOptionValue("s"));
                        sendToken(clientContext.getToken());
                    } else if (event.getMessage() instanceof TokenMessage) {
                        TokenMessage tokenMessage = (TokenMessage) event.getMessage();
                        try {
                            Sspi.SecBufferDesc continueToken = new Sspi.SecBufferDesc(Sspi.SECBUFFER_TOKEN, tokenMessage.getToken());
                            clientContext.initialize(clientContext.getHandle(), continueToken, cmd.getOptionValue("s"));

                            sendToken(clientContext.getToken());
                        } catch (Win32Exception ex) {
                            logger.error("exception receiving token", ex);
                        }
                    }
                }

                private void sendToken(byte[] token) {
                    channelFuture.getChannel().write(new TokenMessage(token));
                    logger.info("sending token to server: " + TokenMessage.byteArrayToHexString(token));
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
                    logger.error("exception caught in server channel handler", e.getCause());
                }

            };
            NioClientSocketChannelFactory channelFactory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
            ClientBootstrap bootstrap = new ClientBootstrap(channelFactory);

            bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
                public ChannelPipeline getPipeline() throws Exception {
                    return Channels.pipeline(
                            new ObjectEncoder(),
                            new ObjectDecoder(),
                            handler);
                }
            });

            channelFuture = bootstrap.connect(new InetSocketAddress(cmd.getOptionValue("n"), Integer.parseInt(cmd.getOptionValue("p"))));
            logger.info("client connected");
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        new Client(args);
    }
}
