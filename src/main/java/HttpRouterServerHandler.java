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
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Map;


@ChannelHandler.Sharable
public class HttpRouterServerHandler extends SimpleChannelInboundHandler<HttpRequest> {

    public static final String PUBLIC_DIR = "public/";
    private final Router<String> router;

    public HttpRouterServerHandler(Router<String> router) {
        this.router = router;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpRequest req) {

        // 405 if request is not GET
        if (req.getMethod() != HttpMethod.GET) {
            HttpResponse res = HttpMethodIsNotGet();
            flushResponse(ctx, req, res);
        }

        RouteResult<String> routeResult = router.route(req.getMethod(), req.getUri());
        Map<String, String> paramMap = null;
        String paramFirst = null;
        if (routeResult.pathParams().isEmpty()) {
            paramMap = null;
        } else {
            paramMap = (Map<String, String>) routeResult.pathParams();
            paramFirst = paramMap.get("id");
        }

        // 400 if any query params
        if (!routeResult.queryParams().isEmpty()) {
            HttpResponse res = invalidQueryParams();
            flushResponse(ctx, req, res);
        }


        if (routeResult.target() == "Custom HTML page") {

            // public/*.jpg *.png
            if (getExtension(paramFirst).equals("jpg") || getExtension(paramFirst).equals("png")) {
                StringBuilder path = new StringBuilder();
                path.append(PUBLIC_DIR);
                path.append(paramFirst);
                HttpResponse res1 = imgResponse(req, router, path.toString());
                flushResponse(ctx, req, res1);
            }

            //public/*.js
            if (getExtension(paramFirst).equals("js")) {
                StringBuilder path = new StringBuilder();
                path.append(PUBLIC_DIR);
                path.append(paramFirst);
                HttpResponse res1 = jsResponse(req, router, path.toString());
                flushResponse(ctx, req, res1);
            }

            //public/*.css
            if (getExtension(paramFirst).equals("css")) {
                StringBuilder path = new StringBuilder();
                path.append(PUBLIC_DIR);
                path.append(paramFirst);
                HttpResponse res1 = cssResponse(req, router, path.toString());
                flushResponse(ctx, req, res1);
            }

            // public/*.*
            HttpResponse res = htmlResponse(req, router);
            flushResponse(ctx, req, res);


        } else { // != "Custom HTML page"
//          HttpResponse res = createResponse(req, router);
          HttpResponse res = blankResponse();
          flushResponse(ctx, req, res);
        }

//        if (routeResult.target() == "base64") {
//            HttpResponse res = base64Response(req, router, "public/encodedImage.txt");
//            flushResponse(ctx, req, res);
//        }
//        if (routeResult.target() == "image") {
//            HttpResponse res = imgResponse(req, router, "public/encodedImage.txt");
//            flushResponse(ctx, req, res);
//        }
    }

    private static HttpResponse cssResponse(HttpRequest req, Router<String> router, String pathString) {

        String content = null;
        try {
            content = new String(Files.readAllBytes(Paths.get(pathString)));
        } catch (NoSuchFileException e) {
            return FileNotFound();
        } catch (IOException e) {
            e.printStackTrace();
        }

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content.toString(), CharsetUtil.UTF_8)
        );

        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "text/css");
        res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, res.content().readableBytes());

        return res;
    }

    private static HttpResponse jsResponse(HttpRequest req, Router<String> router, String pathString) {

        String content = null;
        try {
            content = new String(Files.readAllBytes(Paths.get(pathString)));
        } catch (NoSuchFileException e) {
            return FileNotFound();
        } catch (IOException e) {
            e.printStackTrace();
        }

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content.toString(), CharsetUtil.UTF_8)
        );

        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "application/js");
        res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, res.content().readableBytes());

        return res;


    }

    private static HttpResponse imgResponse(HttpRequest req, Router<String> router, String pathString) {

        RouteResult<String> routeResult = router.route(req.getMethod(), req.getUri());

        byte[] content = null;
        try {
             content = Files.readAllBytes(Paths.get(pathString));
        } catch (NoSuchFileException e) {
            return FileNotFound();
        } catch (IOException e) {
            e.printStackTrace();
        }


        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content)
        );

        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "image/jpg");
        res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, res.content().readableBytes());

        return res;
    }

    private static HttpResponse htmlResponse(HttpRequest req, Router<String> router) {

        RouteResult<String> routeResult = router.route(req.getMethod(), req.getUri());
        StringBuilder targetFile = new StringBuilder();
        targetFile.append(PUBLIC_DIR);

        if (routeResult.pathParams().isEmpty()) {
            targetFile.append("index.html");
        } else {
            Map<String, String> paramMap = (Map<String, String>) routeResult.pathParams();
            String paramFirst = paramMap.get("id");
            targetFile.append(paramFirst);
        }


        String content = null;
        try {
            content = new String(Files.readAllBytes(Paths.get(targetFile.toString())));
        } catch (NoSuchFileException e) {
            return FileNotFound();
        } catch (IOException e) {
            e.printStackTrace();
        }


        boolean isCharsetUSASCII = req.headers().contains("Accept-Charset", "US-ASCII", true);


        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content.toString(), isCharsetUSASCII? CharsetUtil.US_ASCII : CharsetUtil.UTF_8)
        );


        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "text/html");
        res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, res.content().readableBytes());

        return res;
    }

    private static HttpResponse blankResponse() {
        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer("<html><body><a href='public/index.html'>index.html</a></body></html>", CharsetUtil.UTF_8)
        );

        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "text/html");
        res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, res.content().readableBytes());

        return res;
    }

    private static HttpResponse HttpMethodIsNotGet() {
        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED,
                Unpooled.copiedBuffer("405 Request method is not GET", CharsetUtil.UTF_8)
        );

        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "text/plain");
        res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, res.content().readableBytes());

        return res;
    }

    private static HttpResponse FileNotFound() {
        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND,
                Unpooled.copiedBuffer("404 File not Found", CharsetUtil.UTF_8)
        );

        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "text/plain");
        res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, res.content().readableBytes());

        return res;
    }

    private static HttpResponse invalidQueryParams() {
        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST,
                Unpooled.copiedBuffer("400 Bad request", CharsetUtil.UTF_8)
        );

        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "text/plain");
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

    private static HttpResponse base64Response(HttpRequest req, Router<String> router, String pathString) {


        StringBuilder content = new StringBuilder();
        content.append("<!DOCTYPE html><html><head><title></title></head><body style=\"margin:0; padding: 0\"><img src=\"data:image/jpg;base64,");
        try {
            content.append(new String(Files.readAllBytes(Paths.get(pathString))));

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

    private static ChannelFuture flushResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
        if (!HttpHeaders.isKeepAlive(req)) {
            return ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
        } else {
            res.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
            return ctx.writeAndFlush(res);
        }
    }

    public String getExtension(String s) {
        if (s.contains(".")) {
            return s.split("\\.")[1];
        } else return "";

    }
}
