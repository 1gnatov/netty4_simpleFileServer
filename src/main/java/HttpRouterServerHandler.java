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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;


@ChannelHandler.Sharable
public class HttpRouterServerHandler extends SimpleChannelInboundHandler<HttpRequest> {

    public static final String PUBLIC_DIR = "public/";
    public static final boolean FILE_MEMORY_CACHING = false;
    private static final long CACHE_EXPIRES_IN_MS = 60000L; //60sec
    public static final int HTTP_CACHE_SECONDS = 60;
    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String CUSTOM_DATE_FORMAT = "yyyy MMM dd HH:mm:ss";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";

    private final Router<String> router;
    public HashMap<String, cachedStringFile> stringCache = new HashMap<String, cachedStringFile>();
    public HashMap<String, cachedByteArray> byteCache = new HashMap<String, cachedByteArray>();


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

        //Caching in action! =)
//        if (httpCash.containsKey(req.getUri())) {
//            if (httpCash.get(req.getUri()).gotInCache > new Date().getTime() - CACHE_EXPIRES_IN_MS ) {
//                HttpResponse res = httpCash.get(req.getUri()).response;
//                flushResponse(ctx, req, res);
//            } else { // cache got expired
//                httpCash.remove(req.getUri());
//            }
//        }  else { // if (httpCash.containsKey(req.getUri()))

        /* */

        // URI /public/*
        if (routeResult.target() == "Custom HTML page") {

            // 304 if have header IF_MODIFIED_SINCE and file was not mod, also checking FileNotFound
            HttpResponse try304 = checkNotModifiedHeaderAndRespond304(req, paramFirst);
            if (try304 != null) {
                flushResponse(ctx, req, try304);
            }

            // public/*.jpg *.png
            if (getExtension(paramFirst).equals("jpg") || getExtension(paramFirst).equals("png")) {
                StringBuilder path = new StringBuilder();
                path.append(PUBLIC_DIR);
                path.append(paramFirst);
                HttpResponse res1 = imgResponse(req, router, path.toString());
                //httpCash.put(req.getUri(), new cachedReqResLastModTime(req.getUri(), res1, req, new Date().getTime()));
                flushResponse(ctx, req, res1);
            }

            //public/*.js
            if (getExtension(paramFirst).equals("js")) {
                StringBuilder path = new StringBuilder();
                path.append(PUBLIC_DIR);
                path.append(paramFirst);
                HttpResponse res1 = jsResponse(req, router, path.toString());
                //httpCash.put(req.getUri(), new cachedReqResLastModTime(req.getUri(), res1, req, new Date().getTime()));
                flushResponse(ctx, req, res1);
            }

            //public/*.css
            if (getExtension(paramFirst).equals("css")) {
                StringBuilder path = new StringBuilder();
                path.append(PUBLIC_DIR);
                path.append(paramFirst);
                HttpResponse res1 = cssResponse(req, router, path.toString());
                //httpCash.put(req.getUri(), new cachedReqResLastModTime(req.getUri(), res1, req, new Date().getTime()));
                flushResponse(ctx, req, res1);
            }

            // public/*.*
            HttpResponse res = htmlResponse(req, router);
            //httpCash.put(req.getUri(), new cachedReqResLastModTime(req.getUri(), res, req, new Date().getTime()));
            flushResponse(ctx, req, res);


        } else { // != "Custom HTML page"
            HttpResponse res = createResponse(req, router);
//          HttpResponse res = blankResponse();
            //httpCash.put(req.getUri(), new cachedReqResLastModTime(req.getUri(), res, req, new Date().getTime()));
            flushResponse(ctx, req, res);
        }
 //       }
//        if (routeResult.target() == "base64") {
//            HttpResponse res = base64Response(req, router, "public/encodedImage.txt");
//            flushResponse(ctx, req, res);
//        }
//        if (routeResult.target() == "image") {
//            HttpResponse res = imgResponse(req, router, "public/encodedImage.txt");
//            flushResponse(ctx, req, res);
//        }
    }

    public HttpResponse checkNotModifiedHeaderAndRespond304 (HttpRequest req, String pathToFile) {

        String ifModifiedSince = req.headers().get(HttpHeaders.Names.IF_MODIFIED_SINCE);
        String ifNoneMatch = req.headers().get(HttpHeaders.Names.IF_NONE_MATCH);

        File file = new File("public/" + pathToFile);
        if (file.isHidden() || !file.exists()) {
            return FileNotFound();
        }

        Date fileModifDate = new Date(file.lastModified());

        SimpleDateFormat gmtDateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        gmtDateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
        String ifMatchFileLastModifString = gmtDateFormatter.format(fileModifDate);

        String fileEtag = null;
        try {fileEtag = Base64.getEncoder().encodeToString(ifMatchFileLastModifString.getBytes("utf-8")).toLowerCase();} catch (UnsupportedEncodingException e) {};

        // If-None-Match part
        if (ifNoneMatch != null && !fileEtag.isEmpty()) {
            if (ifNoneMatch.equals(fileEtag)) {
                FullHttpResponse res = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_MODIFIED, Unpooled.buffer(0)
                );
                return res;
            }
        }

        // If-Modified-Since part
        if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
            if (ifMatchFileLastModifString.equals(ifModifiedSince)) {
                FullHttpResponse res = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_MODIFIED, Unpooled.buffer(0)
                );
                return res;
            }
        }
        return null;

    }

    private HttpResponse cssResponse(HttpRequest req, Router<String> router, String pathString) {

        String content = null;

        //check cache
        if (stringCache.containsKey(req.getUri()) && FILE_MEMORY_CACHING) {
            if (stringCache.get(req.getUri()).gotInCache > new Date().getTime() - CACHE_EXPIRES_IN_MS) {
                content = stringCache.get(req.getUri()).stringFile;
            } else { // cache got expired
                stringCache.remove(req.getUri());
            }
        } else {

            try {
                content = new String(Files.readAllBytes(Paths.get(pathString)));
            } catch (NoSuchFileException e) {
                return FileNotFound();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content.toString(), CharsetUtil.UTF_8)
        );

        setDateAndCacheHeaders(res, pathString);
        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "text/css");
        res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, res.content().readableBytes());

        //add to cache
        if (FILE_MEMORY_CACHING) stringCache.put(req.getUri(), new cachedStringFile(req.getUri(), content));
        return res;
    }

    private HttpResponse jsResponse(HttpRequest req, Router<String> router, String pathString) {

        //c1 = Files.getLastModifiedTime()

        String content = null;

        //check cache
        if (stringCache.containsKey(req.getUri()) && FILE_MEMORY_CACHING) {
            if (stringCache.get(req.getUri()).gotInCache > new Date().getTime() - CACHE_EXPIRES_IN_MS) {
                content = stringCache.get(req.getUri()).stringFile;
            } else { // cache got expired
                stringCache.remove(req.getUri());
            }
        } else {

            try {
                content = new String(Files.readAllBytes(Paths.get(pathString)));
            } catch (NoSuchFileException e) {
                return FileNotFound();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content, CharsetUtil.UTF_8)
        );

        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "application/js");
        res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, res.content().readableBytes());
        setDateAndCacheHeaders(res, pathString);
        //add to cache
        if (FILE_MEMORY_CACHING) stringCache.put(req.getUri(), new cachedStringFile(req.getUri(), content));
        return res;


    }

    private HttpResponse imgResponse(HttpRequest req, Router<String> router, String pathString) {

        RouteResult<String> routeResult = router.route(req.getMethod(), req.getUri());

        byte[] content = null;

        //check cache
        if (byteCache.containsKey(req.getUri()) && FILE_MEMORY_CACHING) {
            if (byteCache.get(req.getUri()).gotInCache > new Date().getTime() - CACHE_EXPIRES_IN_MS) {
                content = byteCache.get(req.getUri()).byteArray;
            } else { // cache got expired
                byteCache.remove(req.getUri());
            }
        } else {
            try {
                content = Files.readAllBytes(Paths.get(pathString));
            } catch (NoSuchFileException e) {
                return FileNotFound();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content)
        );

        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "image/jpg");
        res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, res.content().readableBytes());
        setDateAndCacheHeaders(res, pathString);
        //add to cache
        if (FILE_MEMORY_CACHING) byteCache.put(req.getUri(), new cachedByteArray(req.getUri(), content));

        return res;
    }

    private HttpResponse htmlResponse(HttpRequest req, Router<String> router) {

        RouteResult<String> routeResult = router.route(req.getMethod(), req.getUri());
        StringBuilder pathToFileSB = new StringBuilder();
        pathToFileSB.append(PUBLIC_DIR);

        if (routeResult.pathParams().isEmpty()) {
            pathToFileSB.append("index.html");
        } else {
            Map<String, String> paramMap = (Map<String, String>) routeResult.pathParams();
            String paramFirst = paramMap.get("id");
            pathToFileSB.append(paramFirst);
        }


        String content = null;

        //check cache
        if (stringCache.containsKey(req.getUri()) && FILE_MEMORY_CACHING) {
            if (stringCache.get(req.getUri()).gotInCache > new Date().getTime() - CACHE_EXPIRES_IN_MS) {
                content = stringCache.get(req.getUri()).stringFile;
            } else { // cache got expired
                stringCache.remove(req.getUri());
            }
        } else {
            try {
                content = new String(Files.readAllBytes(Paths.get(pathToFileSB.toString())));
            } catch (NoSuchFileException e) {
                return FileNotFound();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        boolean isCharsetUSASCII = req.headers().contains("Accept-Charset", "US-ASCII", true);


        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content.toString(), isCharsetUSASCII? CharsetUtil.US_ASCII : CharsetUtil.UTF_8)
        );


        res.headers().set(HttpHeaders.Names.CONTENT_TYPE,   "text/html");
        res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, res.content().readableBytes());
        setDateAndCacheHeaders(res, pathToFileSB.toString());
        //add to cache
        if (FILE_MEMORY_CACHING) stringCache.put(req.getUri(), new cachedStringFile(req.getUri(), content));
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

    private static void setDateAndCacheHeaders(HttpResponse response, String pathToFile) {

        File fileToCache = new File(pathToFile);

        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.headers().set("Date", dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.headers().set(HttpHeaders.Names.EXPIRES, dateFormatter.format(time.getTime()));
        response.headers().set(HttpHeaders.Names.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);

        response.headers().set(HttpHeaders.Names.LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));

        //SimpleDateFormat fileDateFormatter = new SimpleDateFormat(CUSTOM_DATE_FORMAT, Locale.US);
        String lastModifiedString = dateFormatter.format(new Date(fileToCache.lastModified()));
        try {response.headers().set(HttpHeaders.Names.ETAG, Base64.getEncoder().encodeToString(lastModifiedString.getBytes("utf-8")).toLowerCase());} catch (UnsupportedEncodingException e) {}
    }


    class cachedByteArray {
        String uri;
        byte[] byteArray;
        long gotInCache;

        public cachedByteArray(String uri, byte[] byteArray) {
            this.uri = uri;
            this.byteArray = byteArray;
            gotInCache = new Date().getTime();
        }
    }

    class cachedStringFile {
        String uri;

        public cachedStringFile(String uri, String stringFile) {
            this.uri = uri;
            this.stringFile = stringFile;
            gotInCache = new Date().getTime();
        }

        String stringFile;
        long gotInCache;


    }
    //TODO contentType <- mimeTypesMap
//    Can implement contentType as this:
//    private static void setContentTypeHeader(HttpResponse response, File file) {
//        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
//        response.headers().set(CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
//    }
}
