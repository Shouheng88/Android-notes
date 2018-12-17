# 常见的 Gralde 配置和指令总结

## 1、依赖相关

### 1.1 transitive = true

    dependencies {
        implementation ("com.github.bumptech.glide:glide:4.8.0@aar") {
            transitive = true
        }
    }

在后面加上 `@aar`，意指你只是下载该 `aar` 包，而并不下载该 `aar` 包所依赖的其他库，那如果想在使用 `@aar` 的前提下还能下载其依赖库，则需要添加 `transitive=true` 的条件。该属性的默认值是 false，表示你所添加的库的所依赖的其他库会被 Gradle 自动下载。


### 1.2 强制设置某个模块的版本

    configurations.all {
        resolutionStrategy {
            force'org.hamcrest:hamcrest-core:1.3'
        }
    }

### 1.3 强制排除某个依赖

    configurations.all {
        exclude module: 'okhttp-ws'
    }

### 1.3 输出模块的依赖树

按照下面的方式，这里的 `commons` 是模块名：

    gradlew :commons:dependencies

或者点击 AS 右侧的 `Gradle` 选择 `:commons` -> `Tasks` -> `android` -> `:commons:androidDependencies`



