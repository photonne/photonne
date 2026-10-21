# Guía de diseño

[`photonne-cristal.html`](photonne-cristal.html) es la guía visual de **Cristal**, el lenguaje de diseño que comparten la app nativa y la SPA. El cromo flota sobre las fotos en cápsulas de cristal y todo control tiene forma de píldora.

Ábrela en el navegador: es una página autónoma, con maquetas de antes y después que se pueden manejar. Recoge:

- **Tokens**: color, radios, cristal, elevación y movimiento, con los valores de `src/Client.SPA/src/app.css`.
- **Componentes**: shell, navegación móvil, selección, timeline, visor, menús, diálogos, avisos y tarjetas.
- **Reglas** de accesibilidad y rendimiento.
- **Equivalencias** entre la Native (`ui/theme`, `MainScaffold.kt`…) y la SPA, para saber qué tocar en cada lado.
- **Decisiones tomadas** y lo que queda pendiente.

Cuando un cambio de diseño toque tokens o componentes compartidos, actualiza esta guía en la misma PR.
