package com.travel_system.backend_app.infrastructure.aspects;

import com.travel_system.backend_app.annotations.RequireAccess;
import com.travel_system.backend_app.exceptions.AccessRestrictedException;
import com.travel_system.backend_app.model.dtos.ownApi.AccessRestrictionSummary;
import com.travel_system.backend_app.model.dtos.ownApi.RestrictionSubject;
import com.travel_system.backend_app.model.enums.AccessCategory;
import com.travel_system.backend_app.resolvers.RestrictionSubjectResolver;
import com.travel_system.backend_app.service.AccessRestrictionService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static com.travel_system.backend_app.resolvers.AuthenticatedUserResolver.getAuthenticatedUserUUID;

/*
* intercepta toda chamada a um método de controller anotado com @RequireAccess
* antes do método executar decide se lança 403 ou segue normalmente
* */

@Aspect
@Component
public class AccessRestrictionAspect {
    private final Logger log = LoggerFactory.getLogger(AccessRestrictionAspect.class);

    private final RestrictionSubjectResolver restrictionSubjectResolver;
    private final AccessRestrictionService accessRestrictionService;

    public AccessRestrictionAspect(RestrictionSubjectResolver restrictionSubjectResolver, AccessRestrictionService accessRestrictionService) {
        this.restrictionSubjectResolver = restrictionSubjectResolver;
        this.accessRestrictionService = accessRestrictionService;
    }

    @Pointcut("@within(org.springframework.web.bind.annotation.RequireAccess)")
    public void restController() {}

    @Pointcut("@annotation(com.travel_system.backend_app.annotations.RequireAccess) " +
    "|| @within(com.travel_system.backend_app.annotations.RequireAccess)")
    public void requireAccess() {}

    /*
    * intercepta todo método e classe que tenha as anotações @RestController e @RequireAccess
    * */
    @Before("restController() && requireAccess()")
    public void checkAccess(JoinPoint joinPoint) {
        RequireAccess requireAccess = resolveAnnotation(joinPoint);

        log.debug("[checkAccess] checkAccess está rodando, foi chamado normalmente");

        // sem anotação ou escopo que nunca bloqueia segue normalmente
        if (requireAccess == null) {
            return;
        }

        try {
            decide(requireAccess.value());
        } catch (AccessRestrictedException e) {
            // o blqueio deve obrigatoriamente chegar ao handler
            throw e;
        } catch (Exception e) {
            // falha técnica não deve bloquear o user, apenas loga e continua
            log.error("[AccessRestrictionAspect] - falha ao verificar o acesso ao escopo {}", requireAccess.value(), e);
        }
    }

    /*
    * busca a anotação primeiro no método e, se não houver, na classe real do controller
    * atravessa as proxies do spring (caso haja algum endpoint em volta de uma proxy, como um outro aspect)
    * */
    private RequireAccess resolveAnnotation(JoinPoint joinPoint) {
        Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
        Method method = AopUtils.getMostSpecificMethod(((MethodSignature) joinPoint.getSignature()).getMethod(), targetClass);

        RequireAccess onMethod = AnnotatedElementUtils.findMergedAnnotation(method, RequireAccess.class);

        return onMethod != null ? onMethod : AnnotatedElementUtils.findMergedAnnotation(targetClass, RequireAccess.class);
    }

    // lança exception caso o escopo esteja bloqueado para o usuário, caso contrário apenas retorna direto
    private void decide(AccessCategory category) {
        Optional<UUID> userAccountId = getAuthenticatedUserUUID();

        if (userAccountId.isEmpty()) {
            return;
        }

        // categs rápidas que não bloqueiam liberam logo
        if (category.isAlwaysAllowed()) {
            return;
        }

        // se vazio, considera como admins, plataforma ou contas sem customer
        Optional<RestrictionSubject> restrictionSubject = restrictionSubjectResolver.buildRestrictionSubject(userAccountId.get());

        if (restrictionSubject.isEmpty()) {
            return;
        }

        AccessRestrictionSummary summary = accessRestrictionService.getSummary(restrictionSubject.get());

        // verifica se a categoria não é permitida de próposito e se o nível contém essa categoria
        if (summary.lvl().blocks(category)) {
            throw new AccessRestrictedException(
                    "Você está impedido de realizar essa ação.",
                    category,
                    summary.lvl(),
                    summary.restrictions()
            );

        }
    }
}
