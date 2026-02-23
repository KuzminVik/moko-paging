![moko-paging](img/logo.png)  
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0) [![Download](https://img.shields.io/maven-central/v/dev.icerock.moko/paging) ](https://repo1.maven.org/maven2/dev/icerock/moko/paging) ![kotlin-version](https://kotlin-version.aws.icerock.dev/kotlin-version?group=dev.icerock.moko&name=paging)

# Mobile Kotlin paging
This is a Kotlin MultiPlatform library that contains pagination logic for kotlin multiplatform

## Table of Contents
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
- [Samples](#samples)
- [Set Up Locally](#set-up-locally)
- [Contributing](#contributing)
- [License](#license)

## Features
- **Pagination** implements pagination logic for the data from `PagingDataSource`.
- Managing data loading using `loadFirstPage`, `reloadFirstPage`, `loadNextPage`, `refresh`.
- Observing states using `StateFlow` and `RemoteState`.

## Requirements
- Gradle 8.10+
- Android API 16+
- iOS 11.0+

## Installation
root build.gradle  
```groovy
allprojects {
    repositories {
        mavenCentral()
    }
}
```

project build.gradle.kts
```kotlin
dependencies {
    commonMainApi("dev.icerock.moko:paging:0.8.0")
    commonMainApi("dev.icerock.moko:remotestate:0.1.0")
}
```

## Usage

You can use **Pagination** in `commonMain` sourceset.

**Pagination** creation:

```kotlin
val pagination: Pagination<Item> = Pagination(
    dataSource = PageSizePagingDataSource(
        pageSize = 20,
        calculateNextPage = { currentList ->
            // your logic for calculating the next page
        },
        loadPage = { page, pageSize -> repository.load(page = page, pageSize = pageSize) }
    ),
    itemKey = { item -> item.id },
    refreshStrategy = RefreshStrategy.ReplaceEverything,
    nextPageListener = { result ->
        result.onFailure { println("Next page loading failed: $it") }
    },
    refreshListener = { result ->
        result.onFailure { println("Refresh failed: $it") }
    }
)
```

Managing data loading:

```kotlin
// Loading first page
pagination.loadFirstPage()

// Loading next page
pagination.loadNextPage()

// Refreshing pagnation
pagination.refresh()

// Setting new list
pagination.setData(itemsList)
```

Observing **Pagination** states:

```kotlin
val state: RemoteState<PagingState<YourItem>, Throwable> by viewModel.pagination.state.collectAsState()

when (state) {
    RemoteState.Loading -> {
        // ...
    }

    is RemoteState.Error -> {
        // ...
    }

    is RemoteState.Success<PagingState<YourItem>> -> {
        // ...
    }
}
```

## Samples
Please see more examples in the [sample directory](sample).

## Set Up Locally 
- The [paging directory](paging) contains the `paging` library;
- The [sample directory](sample) contains sample apps for Android and iOS; plus the mpp-library connected to the apps.

## Contributing
All development (both new features and bug fixes) is performed in the `develop` branch. This way `master` always contains the sources of the most recently released version. Please send PRs with bug fixes to the `develop` branch. Documentation fixes in the markdown files are an exception to this rule. They are updated directly in `master`.

The `develop` branch is pushed to `master` on release.

For more details on contributing please see the [contributing guide](CONTRIBUTING.md).

## License
        
    Copyright 2020 IceRock MAG Inc.
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
