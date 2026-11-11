package com.udea.demo.usuarios.infrastructure.controller;

import com.udea.demo.usuarios.application.assistant.*;
import com.udea.demo.usuarios.application.service.ActorAuthorizationService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import static com.udea.demo.usuarios.application.assistant.AssistantDTO.*;

@RestController
@RequestMapping("/api/v1/assistant")
@ConditionalOnProperty(name="app.assistant.enabled",havingValue="true")
public class AssistantController {
    private final AssistantService service;
    private final ActorAuthorizationService actors;
    private final AsyncTaskExecutor executor;
    public AssistantController(AssistantService service,ActorAuthorizationService actors,
                               @Qualifier("assistantExecutor") AsyncTaskExecutor executor) {
        this.service=service;this.actors=actors;this.executor=executor;
    }

    @PostMapping("/query")
    public WebAsyncTask<ResponseEntity<Answer>> query(@Valid @RequestBody Query query) {
        actors.operadorActualId();
        long user=actors.actorActual().getId();
        var task=new WebAsyncTask<ResponseEntity<Answer>>(8500L,executor,()->ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.query(user,query)));
        task.onTimeout(()->ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(
                new Answer(null,Result.ERROR,"La consulta tardó más de lo esperado. Intenta nuevamente.")));
        return task;
    }
}
