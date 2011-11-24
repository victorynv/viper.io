package io.viper.core.server.router;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

import java.util.List;
import java.util.Map;

import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class PostRoute extends Route {

  public PostRoute(String route, RouteHandler handler) {
    super(route, handler, HttpMethod.POST);
  }

  @Override
  public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {

    if (!(e instanceof MessageEvent) || !(((MessageEvent) e).getMessage() instanceof HttpRequest))
    {
      super.handleUpstream(ctx, e);
      return;
    }

    HttpRequest request = (HttpRequest) ((MessageEvent) e).getMessage();

    if (request.getMethod() != HttpMethod.POST)
    {
      sendError(ctx, METHOD_NOT_ALLOWED);
      return;
    }

    // extract params

    List<String> path = RouteUtil.parsePath(request.getUri());

    Map<String, String> args = RouteUtil.extractPathArgs(_route, path);

    ChannelBuffer content = request.getContent();

    try
    {
      HttpResponse response = _handler.exec(args);

      if (response == null)
      {
        response = new DefaultHttpResponse(HTTP_1_1, OK);
        response.setContent(wrappedBuffer("{\"status\": true}".getBytes()));
      }

      if (response.getContent() != null && response.getContent().array().length > 0)
      {
        setContentLength(response, response.getContent().array().length);
      }

      ChannelFuture writeFuture = e.getChannel().write(response);
      if (response.getStatus() != HttpResponseStatus.OK || !isKeepAlive(request))
      {
        writeFuture.addListener(ChannelFutureListener.CLOSE);
      }
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }
}
