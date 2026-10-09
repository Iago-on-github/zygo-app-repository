package com.travel_system.backend_app.model.enums;

public enum CustomerPlan {
    LITE(150, 2, 4, 4, 8, 1, 6, 6, 1, 15),
    PRO(350, 3, 6, 6, 15, 3, 10, 30, 1, 25),
    ULTIMATE(500, 5, 12, 12, 30, 5, 15, 45, 3, 50);

    final int maxStudents;
    final int maxAdministrators;
    final int maxDrivers;

    final int maxVehicles;
    final int maxInstitutions;

    final int maxStandardRoutes;
    final int maxStopsPerStandardRoute;
    final int maxTotalRouteStops;

    final int maxResponsiblePerStudent;

    final int maxPendingInvitations;

    /*
    * controle de notificações por plano
    * controle de painel administrativo
    * controle de comunicados (seja email ou notificação)
    * alerta de vencimentos de documentos
    * controle de manutenção de veículos
    * registros de ocorrências
    * otimizações de rota (ordenação dos pontos de parada)
    * operações em dias não úteis
    * controle de adição de documentos
    * controle de identidade visual
    * controle de notificações ao calendário (lembretes automáticos)
    * */

    CustomerPlan(int maxStudents, int maxAdministrators, int maxDrivers, int maxVehicles, int maxInstitutions, int maxStandardRoutes, int maxStopsPerStandardRoute, int maxTotalRouteStops, int maxResponsiblePerStudent, int maxPendingInvitations) {
        this.maxStudents = maxStudents;
        this.maxAdministrators = maxAdministrators;
        this.maxDrivers = maxDrivers;
        this.maxVehicles = maxVehicles;
        this.maxInstitutions = maxInstitutions;
        this.maxStandardRoutes = maxStandardRoutes;
        this.maxStopsPerStandardRoute = maxStopsPerStandardRoute;
        this.maxTotalRouteStops = maxTotalRouteStops;
        this.maxResponsiblePerStudent = maxResponsiblePerStudent;
        this.maxPendingInvitations = maxPendingInvitations;
    }

    public int getMaxStudents() {
        return maxStudents;
    }

    public int getMaxAdministrators() {
        return maxAdministrators;
    }

    public int getMaxDrivers() {
        return maxDrivers;
    }

    public int getMaxVehicles() {
        return maxVehicles;
    }

    public int getMaxInstitutions() {
        return maxInstitutions;
    }

    public int getMaxStandardRoutes() {
        return maxStandardRoutes;
    }

    public int getMaxStopsPerStandardRoute() {
        return maxStopsPerStandardRoute;
    }

    public int getMaxTotalRouteStops() {
        return maxTotalRouteStops;
    }

    public int getMaxResponsiblePerStudent() {
        return maxResponsiblePerStudent;
    }

    public int getMaxPendingInvitations() {
        return maxPendingInvitations;
    }
}
