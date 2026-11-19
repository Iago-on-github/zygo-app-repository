package com.travel_system.backend_app.infrastructure;

import com.travel_system.backend_app.annotations.RequireAccess;
import com.travel_system.backend_app.model.enums.AccessCategory;
import com.travel_system.backend_app.model.enums.RestrictionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static com.travel_system.backend_app.model.enums.RestrictionLevel.allBlockableCategories;

/*
* garante que novas features adventes de novos controllers sempre implementem a restrição de acesso
*
* caso o novo controller não tenha a anotação a aplicação não vai subir e será exibido o que exatamente falta
* */

@Component
@ConditionalOnProperty(prefix = "access-category.verification", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AccessCategoryVerifier implements SmartInitializingSingleton {
    private final Logger log = LoggerFactory.getLogger(AccessCategoryVerifier.class);

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    private static final String PROJECT_BASE_PACKAGE = "com.travel_system.backend_app";

    public AccessCategoryVerifier(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> misconfiguredCategories = findMisconfiguredCategories();

        if (!misconfiguredCategories.isEmpty()) {
            throw new IllegalStateException("Categorias mal configuradas:\n" + String.join("\n", misconfiguredCategories));
        }

        // endpoints sem anotação
        List<String> endpointsWithoutCategory = findEndpointsWithoutCategory();

        if (endpointsWithoutCategory.isEmpty()) {
            log.info("[AccessScopeVerifier] - categorias consistentes e todos os endpoints possuem @RequireAccess.");
            return;
        }

        throw new IllegalStateException(
                endpointsWithoutCategory.size() + " endpoint(s) sem @RequireAccess. Adicione a anotação em:\n" + String.join("\n", endpointsWithoutCategory));
    }

    /*
     * toda categoria precisa estar em algum nível ou ser sempre permitida (nunca as duas coisas)
     * */
    private List<String> findMisconfiguredCategories() {
        Set<AccessCategory> blockable = RestrictionLevel.allBlockableCategories();

        List<String> errors = new ArrayList<>();

        for (AccessCategory category : AccessCategory.values()) {
            boolean inSomeLevel = blockable.contains(category);

            // se a categ não bloquear em nenhum nível e não foi marcada como sempre permitida
            if (!category.isAlwaysAllowed() && !inSomeLevel) {
                errors.add(category + ": não está em nenhum nível nem marcada como sempre permitida.");
            }

            // se a categ foi marcada como livre, mas algum nível a bloqueia
            if (category.isAlwaysAllowed() && inSomeLevel) {
                errors.add(category + ": marcada como sempre permitida, mas está em um nível de bloqueio.");
            }
        }

        return errors;
    }
    /*
    * percorre todas as rotas registradas e devolve, ordenadas por rota, as do projeto
    * que não possuem anotação (a nível de método ou classe)
    * */
    private List<String> findEndpointsWithoutCategory() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();

        List<String> missing = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> methodEntry : handlerMethods.entrySet()) {
            boolean projectEndpoint = isProjectEndpoint(methodEntry.getValue());

            // se for do projeto e não tiver anotação, monta e adiciona a descrição à lista
            if (projectEndpoint && !hasRequiresAccessAnnotation(methodEntry.getValue())) {
                String describe = describe(methodEntry.getKey(), methodEntry.getValue());

                missing.add(describe);
            }

        }

        // devolve a lista ordenada por rota
        return missing.stream().sorted().toList();

    }

    private boolean isProjectEndpoint(HandlerMethod handlerMethod) {
        // getBeanType devolve a classe real do controller e não o proxy do spring
        return handlerMethod.getBeanType().getName().startsWith(PROJECT_BASE_PACKAGE);
    }

    // verifica tanto no método quanto na classe em si
    private boolean hasRequiresAccessAnnotation(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        Class<?> classToVerifier = handlerMethod.getBeanType();

        return AnnotatedElementUtils.hasAnnotation(method, RequireAccess.class) || AnnotatedElementUtils.hasAnnotation(classToVerifier, RequireAccess.class);
    }

    // monta uma rota legível
    // ex.: POST /v1/travel/{travelId}/join -> TravelController#joinTravel
    private String describe(RequestMappingInfo info, HandlerMethod handlerMethod) {
        // métodos http
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();

        // @RequestMapping sem método http definido atende a qualquer método
        String httpMethods = methods.isEmpty()
                ? "ANY"
                : methods.stream().map(Enum::name).sorted().collect(Collectors.joining(", "));

        // um método podendo atender a mais de um caminho
        String paths = String.join(", ", info.getPatternValues());

        return paths + " [" + httpMethods + "] -> " + handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
    }

}
