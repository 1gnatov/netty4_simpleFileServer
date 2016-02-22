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
import jdk.nashorn.internal.ir.annotations.Ignore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

@ChannelHandler.Sharable
public class HttpRouterServerHandler extends SimpleChannelInboundHandler<HttpRequest> {
    private final Router<String> router;

    public HttpRouterServerHandler(Router<String> router) {
        this.router = router;
    }

    @Override @Ignore
    public void channelRead0(ChannelHandlerContext ctx, HttpRequest req) {
//        if (HttpHeaders.is100ContinueExpected(req)) {
//            ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
//            return;
//        }

//        HttpResponse res = customResponse(req, router);

        RouteResult<String> routeResult = router.route(req.getMethod(), req.getUri());

        if (routeResult.target() == "base64") {
            HttpResponse res = base64Response(req, router);
            flushResponse(ctx, req, res);
        }

        if (routeResult.target() == "Custom HTML page") {
            HttpResponse res = customResponse(req, router);
            flushResponse(ctx, req, res);
        } else {
            HttpResponse res = createResponse(req, router);
            flushResponse(ctx, req, res);
        }
        //flushResponse(ctx, req, res);
    }

    private static HttpResponse base64Response(HttpRequest req, Router<String> router) {

        StringBuilder content = new StringBuilder();
        content.append("<!DOCTYPE html><html><head><title></title></head><body style=\"margin:0; padding: 0\"><img src=\"data:image/jpg;base64,");
        try {
            content.append(new String(Files.readAllBytes(Paths.get("public/encodedImage.txt"))));

        } catch (IOException e) {
            e.printStackTrace();
        }
        content.append("\"></body></html>");


        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content.toString(), CharsetUtil.UTF_8)
        );

        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "text/html");
        res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, res.content().readableBytes());

        return res;

    }

    private static HttpResponse customResponse(HttpRequest req, Router<String> router) {

        RouteResult<String> routeResult = router.route(req.getMethod(), req.getUri());
        StringBuilder targetFile = new StringBuilder();
        targetFile.append("public/");

        if (routeResult.pathParams().isEmpty()) {
            targetFile.append("index.html");
        } else {
            Map<String, String> paramMap = (Map<String, String>) routeResult.pathParams();
            String paramFirst = paramMap.get("id");
            targetFile.append(paramFirst);
        }
        System.out.println(targetFile);



        String content = null;
        try {
            content = new String(Files.readAllBytes(Paths.get(targetFile.toString())));
            //content = new String(Files.readAllBytes(Paths.get("index.html")));
        } catch (IOException e) {
            e.printStackTrace();
        }



        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content.toString(), CharsetUtil.UTF_8)
        );


//        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "image/png");
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
