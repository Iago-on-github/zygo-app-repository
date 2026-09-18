package com.travel_system.backend_app.infrastructure;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.travel_system.backend_app.service.*.*(..))")
    public void applyTenantFilter() {
        applyFilter();
    }

    // chamado manualmente em métodos async que acessam o banco
    public void applyFilter() {
        UUID currentTenant = TenantContext.getCurrentTenant();

        if(currentTenant != null) {
            Session session = entityManager.unwrap(Session.class);

            if (session.getEnabledFilter("tenantFilter") == null) {
                session.enableFilter("tenantFilter").setParameter("customerId", currentTenant);
            }
        }

    }
}

/*
* GUIDE
* @Aspect intercepta a execução de qualquer metodo em qualquer classe dentro do package ".service"
* @Aspect executa dentro do contexto transacional (na mesma Session) do Hibernate que o repo usa para as requisições
* Como funciona o processo básico:
* Requisição chega
  → JwtAuthenticationFilter → popula TenantContext
  → Controller chama o Service
    → @Before intercepta ANTES do metodo do service executar
      → lê TenantContext.getCurrentTenant()
      → ativa o filtro Hibernate na sessão atual
    → metodo do service executa
      → repositório faz a query COM o filtro ativo
* */
