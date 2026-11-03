# Ficha para la tienda de Roku — Facst Play TV

Todo lo que pide el panel de desarrolladores de Roku, listo para copiar.
Lo que solo puede hacer el dueño de la cuenta está marcado con **(tú)**.

## 1. Firmar el paquete (.pkg)
1. La app ya instalada en el Roku de prueba (`http://192.168.100.5` → **Upload** → **Replace**) con el zip más reciente.
2. En `http://192.168.100.5` → **Packager**:
   - **App name/version**: `FacstPlayTV/1.0.1`
   - **Password**: la contraseña de la llave de firma actual (la misma de Ultra Premier TV, DevID `2667e569…`).
3. **Package** y baja el archivo `.pkg` que aparece.
   Todas las versiones futuras de Facst Play TV se firman con esta misma llave.

## 2. Datos de la ficha (idioma principal: español; añade inglés)
- **Nombre**: `Facst Play TV`
- **Categoría**: Movies & TV
- **Tipo de app**: gratuita, sin compras dentro de la app, requiere iniciar sesión
- **Cartel de la tienda**: `roku/images/icon_fhd.png` (540×405) y `roku/images/icon_hd.png` (290×218)
- **Capturas** (1920×1080): **(tú)** en `http://192.168.100.5` → **Utilities → Screenshot** (inicio, en vivo, películas, ficha y reproducción).
- **Política de privacidad**: <https://github.com/nunezvaleria227-beep/facst-play-tv/blob/main/docs/PRIVACIDAD.md>
- **Términos**: <https://github.com/nunezvaleria227-beep/facst-play-tv/blob/main/docs/TERMINOS.md>
- **Sitio web / soporte**: <https://github.com/nunezvaleria227-beep/facst-play-tv/issues>
- **Contactos administrativo y técnico**: **(tú)** tu nombre y tu correo.
- **Cuenta de prueba** para los revisores de Roku: **(tú)** un usuario y contraseña que funcione y no venza pronto.

### Descripción en el dispositivo (máx. 300)
Español:
> Tus canales en vivo, películas y series en un solo lugar, con un diseño premium. Entra con el usuario y la contraseña de tu proveedor, descubre lo más nuevo desde el inicio, retoma lo que dejaste a medias y guarda tus favoritos en Mi lista. Incluye control parental.

English:
> Your live channels, movies and series in one place, with a premium design. Sign in with the username and password from your provider, discover what's new right from the home screen, pick up where you left off and save favorites to My List. Includes parental control.

### Descripción en línea (máx. 1500), español
```
Facst Play TV reúne en una sola app la televisión en vivo, las películas y las series a las que ya tienes acceso con tu proveedor de televisión por internet, con un diseño moderno pensado para la tele.

Entra con el usuario y la contraseña que te dio tu proveedor y la sesión queda guardada.

• Inicio con lo más nuevo: una portada grande con imagen, sinopsis y "Ver ahora", y filas de películas y series recientes.
• TV en vivo con guía de "ahora / después" y cambio de canal con las flechas.
• Películas y series con ficha, temporadas y episodios; al terminar un episodio pasa al siguiente.
• "Continuar viendo": retoma justo donde te quedaste.
• Mi lista y búsqueda por nombre, sin importar acentos ni mayúsculas.
• Subtítulos y audio incluidos en el video: se elige español o inglés automáticamente cuando existen.
• Control parental con PIN: oculta por defecto las categorías para adultos.

Importante: la app no incluye contenido propio ni suscripciones. Necesitas una cuenta vigente con un proveedor de televisión por internet.
```

### Online description, English
```
Facst Play TV brings live TV, movies and series together in one app, using the access you already have with your internet-TV provider, with a modern design made for the big screen.

Sign in with the username and password from your provider and your session is remembered.

• A home screen with what's new: a large featured banner with artwork, synopsis and "Watch now", plus rows of recent movies and series.
• Live TV with a "now / next" guide and channel zapping with the arrow keys.
• Movies and series with details, seasons and episodes; the next episode plays automatically.
• "Continue watching": resume exactly where you left off.
• My List and search by name, ignoring accents and capitalization.
• Subtitles and audio tracks included in the video: Spanish or English is picked automatically when available.
• Parental control with a PIN: adult categories are hidden by default.

Important: the app does not include any content or subscriptions of its own. You need an active account with an internet-TV provider.
```

## 3. Enlaces directos (deep linking)
| Tipo de contenido (mediaType) | contentId | Qué abre |
|---|---|---|
| `movie` | id de la película (`stream_id`) | ficha de la película (empieza a reproducir) |
| `series` | id de la serie (`series_id`) | la serie (reproduce donde se quedó o el primer episodio) |
| `live` | id del canal (`stream_id`) | el canal en vivo |

## 4. Revisión de Roku
- Pasa el **Static Analysis** del panel antes de enviar; si marca errores, mándamelos.
- Al enviar, **(tú)** confirmas la lista de verificación y certificas que tienes los derechos del contenido.
- La publicación más temprana es el segundo día hábil después de aprobada.
