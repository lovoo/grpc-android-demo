package com.lovoo.ubuntudroid.hellogrpc;

/**
 * Copyright (c) 2015, LOVOO GmbH
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of LOVOO GmbH nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerImpl;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.helloworld.HelloResponse;
import io.grpc.stub.StreamObserver;
import io.grpc.transport.netty.NettyServerBuilder;

/**
 * A simple GRPC server implementation which understands the protocol defined in hello.proto.
 *
 * Inspired by
 * <a href="https://github.com/grpc/grpc-java/blob/master/interop-testing/src/main/java/io/grpc/testing/integration/TestServiceServer.java">grpc-java's TestServiceServer</a>,
 * but radically simplified.
 *
 * When changing the proto file you also have to adapt the {@link #start()} method. For more complex
 * implementations extracting the service implementation in an own class is advised.
 */
public class Server {
    /**
     * The main application allowing this server to be launched from the command line.
     */
    public static void main ( String[] args ) throws Exception {
        final Server server = new Server();
        server.parseArgs(args);
        if (server.useTls) {
            System.out.println(
                    "\nUsing fake CA for TLS certificate. Test clients should expect host\n"
                            + "*.test.google.fr and our test CA. For the Java test client binary, use:\n"
                            + "--server_host_override=foo.test.google.fr --use_test_ca=true\n");
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    System.out.println("Shutting down");
                    server.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        server.start();
        System.out.println("Server started on port " + server.port);
    }

    private int port = 8080;
    private boolean useTls = false;

    private ScheduledExecutorService executor;
    private ServerImpl server;

    private void parseArgs(String[] args) {
        boolean usage = false;
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                System.err.println("All arguments must start with '--': " + arg);
                usage = true;
                break;
            }
            String[] parts = arg.substring(2).split("=", 2);
            String key = parts[0];
            if ("help".equals(key)) {
                usage = true;
                break;
            }
            if (parts.length != 2) {
                System.err.println("All arguments must be of the form --arg=value");
                usage = true;
                break;
            }
            String value = parts[1];
            if ("port".equals(key)) {
                port = Integer.parseInt(value);
            } else if ("use_tls".equals(key)) {
                useTls = Boolean.parseBoolean(value);
            } else if ("grpc_version".equals(key)) {
                if (!"2".equals(value)) {
                    System.err.println("Only grpc version 2 is supported");
                    usage = true;
                    break;
                }
            } else {
                System.err.println("Unknown argument: " + key);
                usage = true;
                break;
            }
        }
        if (usage) {
            Server s = new Server();
            System.out.println(
                    "Usage: [ARGS...]"
                            + "\n"
                            + "\n  --port=PORT           Port to connect to. Default " + s.port
                            + "\n  --use_tls=true|false  Whether to use TLS. Default " + s.useTls
            );
            System.exit(1);
        }
    }

    private void start() throws Exception {
        executor = Executors.newSingleThreadScheduledExecutor();
        server = NettyServerBuilder.forPort(port)
                .addService(ServerInterceptors.intercept(
                        GreeterGrpc.bindService(new GreeterGrpc.Greeter() {
                            @Override
                            public void sayHello ( HelloRequest request, StreamObserver<HelloResponse> responseObserver ) {
                                responseObserver.onValue(HelloResponse.newBuilder().setMessage("Hello " + request.getName()).build());
                                responseObserver.onCompleted();
                            }
                        }),
                        new ServerInterceptor() {
                            @Override
                            public <RequestT, ResponseT> ServerCall.Listener<RequestT> interceptCall ( String method,
                                                                                                       ServerCall<ResponseT> serverCall,
                                                                                                       Metadata.Headers headers,
                                                                                                       ServerCallHandler<RequestT, ResponseT> serverCallHandler ) {
                                System.out.println("Received call to " + method);
                                return serverCallHandler.startCall(method, serverCall, headers);
                            }
                        }))
                .build().start();
    }

    private void stop() throws Exception {
        server.shutdownNow();
        if (!server.awaitTerminated(5, TimeUnit.SECONDS)) {
            System.err.println("Timed out waiting for server shutdown");
        }
        MoreExecutors.shutdownAndAwaitTermination(executor, 5, TimeUnit.SECONDS);
    }
}
