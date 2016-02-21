/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.router.RouteResult;
import io.netty.handler.codec.http.router.Router;
import io.netty.util.CharsetUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@ChannelHandler.Sharable
public class HttpRouterServerHandler extends SimpleChannelInboundHandler<HttpRequest> {
    private final Router<String> router;

    public HttpRouterServerHandler(Router<String> router) {
        this.router = router;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpRequest req) {
        if (HttpHeaders.is100ContinueExpected(req)) {
            ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
            return;
        }

        HttpResponse res = customResponse(req, router);
        //HttpResponse res = createResponse(req, router);
        flushResponse(ctx, req, res);
    }

    private static HttpResponse customResponse(HttpRequest req, Router<String> router) {
        RouteResult<String> routeResult = router.route(req.getMethod(), req.getUri());

        String content = null;
        try {
            content = new String(Files.readAllBytes(Paths.get("index.html")));
        } catch (IOException e) {
            e.printStackTrace();
        }


        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content.toString(), CharsetUtil.UTF_8)
        );

        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "text/html");
        res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, res.content().readableBytes());

        return res;
    }

    private static HttpResponse createResponse(HttpRequest req, Router<String> router) {
        RouteResult<String> routeResult = router.route(req.getMethod(), req.getUri());

        // Display debug info.
        //
        // For simplicity of this example, route targets are just simple strings.
        // But you can make them classes, and here once you get a target class,
        // you can create an instance of it and dispatch the request to the instance etc.
        StringBuilder content = new StringBuilder();
        content.append("router: \n" + router + "\n");
        content.append("req: " + req + "\n\n");
        content.append("routeResult: \n");
        content.append("target: " + routeResult.target() + "\n");
        content.append("pathParams: " + routeResult.pathParams() + "\n");
        content.append("queryParams: " + routeResult.queryParams() + "\n\n");
        content.append("allowedMethods: " + router.allowedMethods(req.getUri()));

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content.toString(), CharsetUtil.UTF_8)
        );

        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "text/plain");
        res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, res.content().readableBytes());

        return res;
    }

    private static ChannelFuture flushResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
        if (!HttpHeaders.isKeepAlive(req)) {
            return ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
        } else {
            res.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
            return ctx.writeAndFlush(res);
        }
    }
}
