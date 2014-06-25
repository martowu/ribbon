package com.netflix.ribbonclientextensions;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.util.Map;

import rx.Observable;
import rx.functions.Func1;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.ribbonclientextensions.http.HttpRequestTemplate;
import com.netflix.ribbonclientextensions.http.HttpResourceGroup;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;


public class RibbonExamples {
    public static void main(String[] args) {
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("myclient");
        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("GetUser")
        .withResponseValidator(new ResponseValidator<HttpClientResponse<ByteBuf>>() {

            @Override
            public void validate(HttpClientResponse<ByteBuf> response)
                    throws UnsuccessfulResponseException, ServerError {
                if (response.getStatus().code() >= 500) {
                    throw new ServerError("Unexpected response");
                }
            }
        })   
        .withFallbackProvider(new FallbackHandler<ByteBuf>() {
            @Override
            public Observable<ByteBuf> getFallback(HystrixExecutableInfo<?> t1, Map<String, Object> vars) {
                return Observable.empty();
            }
        })
        .withHystrixProperties((HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("mygroup"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationThreadTimeoutInMilliseconds(2000))))
        .withUri("/{id}");
        
        template.requestBuilder().withRequestProperty("id", 1).build().execute();
        
        // example showing the use case of getting the entity with Hystrix meta data
        template.withUri("/{id}").requestBuilder().withRequestProperty("id", 3).build().withMetadata().observe()
            .flatMap(new Func1<RibbonResponse<Observable<ByteBuf>>, Observable<String>>() {
                @Override
                public Observable<String> call(RibbonResponse<Observable<ByteBuf>> t1) {
                    if (t1.getHystrixInfo().isResponseFromFallback()) {
                        return Observable.empty();
                    } 
                    return t1.content().map(new Func1<ByteBuf, String>(){
                        @Override
                        public String call(ByteBuf t1) {
                            return t1.toString();
                        }
                        
                    });
                }
            });
    }

}
