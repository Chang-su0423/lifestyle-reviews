package com.hmdp.config.rabbitmqConfig;


import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqErrorConfig {

    @Bean
    public DirectExchange errorExchange(){
        return ExchangeBuilder
                .directExchange("error.exchange")
                .durable(true)
                .build();
    }

    @Bean
    public Queue errorQueue(){
        return QueueBuilder.durable("error.queue").build();
    }

    @Bean
    public Binding errorBinding(){
        return BindingBuilder.bind(errorQueue()).to(errorExchange()).with("error");
    }

    @Bean
    public MessageRecoverer republishMessageRecoverer(RabbitTemplate rabbitTemplate){
        return new RepublishMessageRecoverer(rabbitTemplate, "error.exchange", "error");
    }
}
