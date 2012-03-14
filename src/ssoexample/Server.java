package ssoexample;

import com.sun.jna.platform.win32.Win32Exception;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.jboss.netty.handler.codec.serialization.ObjectEncoder;
import waffle.windows.auth.IWindowsSecurityContext;
import waffle.windows.auth.impl.WindowsAuthProviderImpl;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Server {
    private final Logger logger = Logger.getLogger(getClass());
    private final WindowsAuthProviderImpl provider = new WindowsAuthProviderImpl();

    public Server(String[] args) {
        try {
            Options options = new Options();
            options.addOption("n", true, "server hostname");
            options.addOption("p", true, "server port");
            options.addOption("h", false, "print this message");

            CommandLine cmd = new PosixParser().parse(options, args);

            if (cmd.hasOption("h") || !(cmd.hasOption("n") && cmd.hasOption("p"))) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("server", options);
                System.exit(0);
            }

            NioServerSocketChannelFactory channelFactory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
            ServerBootstrap bootstrap = new ServerBootstrap(channelFactory);

            bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
                public ChannelPipeline getPipeline() throws Exception {
                    return Channels.pipeline(
                            new ObjectEncoder(),
                            new ObjectDecoder(),
                            new SimpleChannelUpstreamHandler() {
                                @Override
                                public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) throws Exception {
                                    if (!(event.getMessage() instanceof TokenMessage)) {
                                        logger.info("message received: " + event.getMessage());
                                    }
                                    if (event.getMessage() instanceof HelloMessage) {
                                        event.getChannel().write(new AuthorizationRequiredMessage());
                                    } else if (event.getMessage() instanceof TokenMessage) {
                                        TokenMessage tokenMessage = (TokenMessage) event.getMessage();
                                        logger.info("token recieved from client: length: " + tokenMessage.getToken().length + " content: " + TokenMessage.byteArrayToHexString(tokenMessage.getToken()));
                                        String securityPackage = "Negotiate";
                                        IWindowsSecurityContext serverContext;
                                        try {
                                            serverContext = provider.acceptSecurityToken("server-connection", tokenMessage.getToken(), securityPackage);
                                            if (serverContext.getContinue()) {
                                                byte[] token = serverContext.getToken();
                                                logger.info("sending token to client: length:  " + token.length + " content: " + TokenMessage.byteArrayToHexString(token));
                                                event.getChannel().write(new TokenMessage(token));
                                            } else {
                                                logger.info("user " + serverContext.getIdentity().getFqn() + " logged in");
                                                event.getChannel().write(new AuthorizedMessage());
                                            }
                                        } catch (Win32Exception e) {
                                            logger.error("exception receiving token", e);
                                            event.getChannel().write(new NotAuthorizedMessage());
                                        }
                                    }
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
                                    logger.error("exception caught in server channel handler", e.getCause());
                                }
                            });
                }
            });

            bootstrap.bind(new InetSocketAddress(cmd.getOptionValue("n"), Integer.parseInt(cmd.getOptionValue("p"))));
            logger.info("server started");
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        new Server(args);
    }
}
