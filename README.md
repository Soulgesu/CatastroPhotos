# 📸 CatastroPhotos

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-orange.svg)](https://kotlinlang.org/)
[![Modern Android Development](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Flow-blue.svg)](https://developer.android.com/topic/architecture)

**CatastroPhotos** es una aplicación profesional de captura y gestión fotográfica diseñada específicamente para el relevamiento catastral en campo. Optimiza el flujo de trabajo organizando archivos por sectores y manzanas, proporcionando una interfaz fluida, reactiva y robusta.

---

## ✨ Características Principales

- **Organización Automática**: Clasificación instantánea de fotos por Carpetas (Sector/Manzana).
- **Cámara Profesional**:
    - Enfoque manual mediante toque (Single Tap).
    - Zoom de precisión (Pinch-to-zoom).
    - Captura optimizada para baja latencia.
- **Galería Inteligente**:
    - **Sincronización Contextual**: Refresco automático de fotos y carpetas en tiempo real.
    - **Indicadores de Estado (⚠️)**: Rastreo visual de cambios pendientes de exportación.
    - **Modo Selección**: Borrado y exportación masiva de elementos.
- **Exportación Robusta**: Compresión instantánea en formato ZIP con organización jerárquica para entrega inmediata.
- **Rendimiento de Alta Gama**: Sistema de caché en memoria que elimina esperas de lectura en disco.

---

## 🏗️ Arquitectura Técnica

La aplicación sigue los principios de **Modern Android Development (MAD)** y **Clean Architecture**:

- **MVVM (Model-View-ViewModel)**: Separación estricta de la UI y la lógica de negocio para garantizar la estabilidad y facilidad de pruebas.
- **Repository Pattern**: Centralización de la gestión de datos (MediaStore, SharedPreferences, Cache) en una única fuente de verdad.
- **Programación Reactiva**: Uso extensivo de **Kotlin Coroutines** y **Flow/StateFlow** para una interfaz que responde instantáneamente a cambios en los datos.
- **ListAdapter + DiffUtil**: Optimización de RecyclerViews para animaciones suaves y un rendimiento eficiente durante el desplazamiento.

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología |
| :--- | :--- |
| **Visor de Cámara** | CameraX API |
| **Carga de Imágenes** | Coil (Efficient Image Loading) |
| **Inyección de Dependencias** | Provider Pattern / Factory (Escalable a Hilt) |
| **Gestión de Memoria** | In-Memory Singleton Cache |
| **Material Design** | Material Components 3 (M3) |
| **Persistencia** | SharedPreferences & MediaStore API |

---

## 📁 Estructura del Proyecto

```text
app/src/main/java/com/desito/catastrophotos/
├── ui/
│   ├── CameraFragment.kt    # Interfaz de captura líder en velocidad
│   ├── GalleryFragment.kt   # Gestión reactiva de lotes y fotos
│   └── adapters/            # ListAdapters con DiffUtil optimizado
├── data/
│   ├── MediaRepository.kt   # Singleton de datos y lógica de archivos
│   └── UIState.kt           # Modelos de datos inmutables
└── viewmodels/
    ├── CameraViewModel.kt   # Lógica de inputs de campo
    └── GalleryViewModel.kt  # Gestión de estados de sincronización
```

---

## 🚀 Instalación y Uso

1. Clonar el repositorio.
2. Abrir con **Android Studio Ladybug** o superior.
3. Compilar (`gradle build`) y ejecutar en dispositivos con Android 8.0+.
4. **Captura**: Ingresa Sector/Manzana y toma la foto.
5. **Gestiona**: Visualiza en la galería y usa la selección múltiple para borrar o compartir.
6. **Exporta**: Selecciona carpetas y usa el icono de compartir para generar el ZIP final.

---

> [!NOTE]
> Esta herramienta ha sido diseñada para ser ligera y funcionar en entornos de alta demanda operativa, minimizando el consumo de batería y maximizando la velocidad de respuesta.

---
© 2026 CatastroPhotos Team.
