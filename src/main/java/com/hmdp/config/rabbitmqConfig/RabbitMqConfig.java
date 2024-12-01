package com.hmdp.config.rabbitmqConfig;


import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

   @Bean
    public Queue queue(){
       return QueueBuilder
               .durable("sekill_queue")
               .lazy() // 开启Lazy模式
               .build();
   }

   @Bean
    public FanoutExchange exchange(){
       return new FanoutExchange("sekill",true,false);
   }

   @Bean
    public Binding binding(Queue queue, FanoutExchange exchange){
       return BindingBuilder.bind(queue).to(exchange);
   }
}
