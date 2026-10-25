# Revisión obligatoria de compatibilidad de la API con Android

## Regla

Cada cambio del backend de DietoExpress que toque un endpoint o su comportamiento debe evaluar explícitamente el impacto en la app nativa `jl-hita/DietoExpress.Android`.

Esto incluye, como mínimo:

- Añadir, cambiar rutas, verbos o eliminar endpoints.
- Cambiar request/response DTO, campos requeridos, nombres, tipos, nullabilidad o formatos de fecha.
- Cambiar autenticación, cookie de sesión, expiración, logout, claims o códigos HTTP.
- Cambiar autorización por rol/tenant, reglas de acceso a pacientes/citas/documentos/vídeo.
- Cambiar validaciones, paginación, orden, estados, límites, efectos secundarios o reglas de negocio.
- Cambiar configuración CORS/HTTPS/cookies, upload/download, enlaces profundos o notificaciones.

## Checklist para PRs del backend

- [ ] He identificado los endpoints y contratos afectados.
- [ ] He revisado el uso de esos contratos en `DietoExpress.Android` (búsqueda de ruta, DTO y flujo funcional).
- [ ] He marcado explícitamente una de estas opciones en la descripción del PR: **sin impacto en Android** (con motivo) o **requiere cambios en Android** (con enlace al PR/issue correspondiente).
- [ ] He actualizado pruebas de contrato/integración o explicado por qué no son necesarias.
- [ ] Si el cambio es incompatible, he coordinado el orden de despliegue y la compatibilidad con versiones publicadas.
- [ ] He comprobado que no se filtran datos sensibles en respuestas, logs ni notificaciones.

## Checklist para PRs Android

- [ ] El cliente usa el contrato real del backend; no se han inventado campos ni rutas.
- [ ] Se cubren estados de éxito, error, sesión expirada y respuestas incompletas.
- [ ] Los permisos se verifican en el servidor; la navegación/visibilidad de UI no se considera seguridad.
- [ ] Los cambios de contrato tienen pruebas relevantes.

## Proceso recomendado

1. Localizar el endpoint/controlador y DTO en el backend.
2. Localizar sus consumidores web y Android.
3. Actualizar el contrato y las pruebas en el mismo cambio cuando sea viable; si son repos separados, enlazar ambos PRs.
4. No desplegar un cambio incompatible antes de que la app publicada pueda tolerarlo, salvo que se haya acordado una migración coordinada.
5. Registrar explícitamente la revisión incluso cuando la conclusión sea “sin impacto”.
