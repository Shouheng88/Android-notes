# Kotlin 学习笔记-1：基础语法

## 1、初识 Kotlin

### 1.1 基本特性梳理

1. 本质上是**静态类型语言**，编译期确定类型，**但无需明确指定变量类型**；
2. 对**可空类型的支持**，可以在编译期发现空指针；
3. 支持**函数式编程**，虽然 Java 8 以后都支持了；
4. 类文件的后缀名式 `.kt`，编译之后还是生成 class 文件，只是编译器使用的是 `kotlinc`（对应于 javac），执行 class 的时候还是使用 java；
5. 可以使用转换器将 Java 转换成 kotlin；
6. Kotlin 标准库给 Java 库做了封装，我们可以简化原生 Java 库的调用；

### 1.2 类文件结构

在 Kotlin 中文件名称和文件的内容没有关系（在 Java 中文件名和类名相同），并且文件内部定义的是函数还是类都没关系。比如，下面是定义在目录 `me/shouheng/demo1/FirstDemo.kt` 中的类和函数，这里类和函数处于文件的同一层次。另外，一个文件中还可以定义多个类和多个函数，都是允许的。

```kotlin
package me.shouheng                                 // 包的声明应处于源文件顶部

class Person (age : Int, name : String)             // 声明了一个类
class Person2 (val age : Int, val name : String)    // 声明了一个类
```

类和函数的真实包名是由文件中的 **package** 关键字指定的，与文件结构没有必然的关系。当然，我们建议按照 Java 的规则使其对应起来，因为这样维护起来更好、逻辑更清晰些。

### 1.3 定义函数

```kotlin
fun doSomething(person: Person) : Int {             // 定义了一个函数
    // 在字符串中使用 “$+变量名” 的格式进行占位，相当于 "My name is" + persion + "!"
    println("My name is $person!")
}

fun sum(a: Int, b: Int) = a + b
```

1. 函数的定义使用关键字 `fun`，覆写函数的话就在函数名前面加上 `override fun`。
2. 变量和返回值的类型被放在冒号后面，如果返回无意义的类型，可以使用 `Unit`，也可以省略。
3. **字符串模板**：在字符串中使用 `$+变量名` 的格式进行占位（这叫字符串模板），如果希望使用美元符号，前面加上反斜杠即可。
4. 也可以将表达式作为函数体、返回值类型自动推断。
5. 如果需要把一个字符串当作正则表达式，需要显式调用字符串的 `toRegex()` 方法才行。
6. 三重引号中的字符不会做任何转义，即 `"""$"""` 可以直接当作美元。

### 1.4 定义变量

```kotlin
fun test(args : Array<String>) {
    val a = Person(10, "Ming")
    var b: Person
    b = Person(11, "Xing")
    doSomething(a)
}
```

1. **数组**：没有专门用来声明数组的，全部都是类。可以像下面这样声明数组 `Array<String>`。另外，可以按照 `args[0]` 的方式获取数组元素。
2. **声明变量有 `var` 和 `val` 两中方式**：系统可以自动推断类型，`var` 声明的变量可以二次赋值，而 `val` 不行，后者相当于 `final` 的。
3. 虽然 `var` 类型的变量可以二次赋值，但是两次赋值的类型必须相同。
4. 可以在声明变量的时候使用冒号指定变量的类型，像上面的 b 一样（大部分情况下可以省略，因为编译器可以自动推断）。
5. 初始化一个类的时候不需要 `new` 关键字（抛出异常的时候自然也一样）。

### 1.5 使用循环

```kotlin               
    // 循环 Map
    val map = HashMap<Char, String>()
    for (c in 'A'..'F') {           // 循环字符串
        map[c] = Integer.toHexString(c.toInt())
    }
    for ((k, v) in map) { // 输出结果是 <A,41> <B,42> <C,43> <D,44> <E,45> <F,46>
        print("<$k,$v> ")
    }

    // 循环列表
    val items = listOf("apple", "banana", "kiwifruit")
    for (index in items.indices) {
        println("item at $index is ${items[index]}")
    }

    // while 循环
    var index = 0
    while (index < items.size) {    // 使用 while 循环
        println("item at $index is ${items[index]}")
        index++
    }
```

总结，

1. Kotlin 的 for 循环与 Java 稍有不同，它跟 js 等更相似，即使用 `in` 关键字。
2. 遍历 map 的时候使用上述方式，以键值对的形式遍历即可。
3. 要按照索引的方式进行遍历，需要先使用列表的 `indices` 得到索引列表。
4. while 循环和 Java 中的使用方式基本一致，包括 `while` 和 `do...while` 两种形式。

### 1.6 when

类似于 Java 中的 switch，但是它的每个条件中默认加入了 break. 另外，它还有一个比较好的地方是，它会检查枚举是否都包含进去了，如果没全部包含，它会提示你全包含或者加入 else 语句。

```kotlin
fun multiple(city: City2) = when(city) {
    City2.BEIGING -> {
        2*10000
        2+2
    }
    City2.SHANGHAI,City2.GUANGZHOU->3*10000
    else -> 5
}

fun describe(obj: Any): String =
    when (obj) {
        1          -> "One"
        "Hello"    -> "Greeting"
        is Long    -> "Long"
        !is String -> "Not a string"
        else       -> "Unknown"
    }
```

另外，从示例 2 中可以看出，

1. 注意每个条件之后需要加上 `->` 才行哦！
2. 当多个类型的逻辑相同的时候，可以把它们放在 when 的一个条件里，然后用逗号分隔开。
3. when 比 switch 的功能更加强大，它还可以使用不同类型的判断条件。（参考示例 2）

### 1.7 控制语句

#### 1.7.1 if 语句

在 Kotlin 中，if是一个表达式，即它会返回一个值。 因此就不需要三元运算符 `? : `，比如 `val max = if (a > b) a else b`。

#### 1.7.2 返回与跳转

Kotlin 中返回与跳转语句也是 return、break 和 continue 三种。它们的基本使用方式与 Java 相同。此外，Kotlin 中还支持标签。标签的格式为标识符后跟 @ 符号，例如：`abc@`、`fooBar@` 都是有效的标签。我们可以使用标签进行流程的控制（用的比较少）。

### 1.8 异常处理

`try..catch` 语句的基本结构如下，和 Java 基本相似，只是 catch 中声明变量的方式，下面的函数会当小于 0 时返回 -1，否则返回 1. 另外，kotlin 中不分受检异常和非受检异常，不会强制你捕获异常。

```kotlin
fun tryTest(i : Int) = try {
    if (i < 0) throw IllegalArgumentException("< 0")
    else 1
} catch (e : Exception) {
    -1
}
```

## 2、类与对象

### 2.1 类声明

Kotlin 中使用关键字 `class` 声明类。类声明由类名、类头（指定其类型参数、主构造函数等）以及由花括号包围的类体构成。类头与类体都是可选的；如果一个类没有类体，可以省略花括号。

```kotlin
class MyClass { /*...*/ }
class Empty
```

### 2.2 构造方法

在 Kotlin 中的一个类可以有一个`主构造函数`以及一个或多个`次构造函数`。主构造函数是类头的一部分：它跟在类名（与可选的类型参数）后。如果主构造函数没有任何注解或者可见性修饰符，可以省略这个 `constructor` 关键字。

```kotlin
class Person constructor(firstName: String) { ... } // 主构造函数
class Person(firstName: String) { ... }             // 省略主构造函数
class Person(val firstName: String) { ... } 
class DontCreateMe private constructor () { ... }   // 将构造函数设置成私有的
```

注意上述声明方式中的 2 和 3 的区别，后者声明之后有一个局部变量 firstName，而前者没有声明任何变量。可以通过 private 关键字将构造函数设置成私有的。

类也可以声明前缀有 constructor 的次构造函数。果类有一个主构造函数，每个次构造函数需要委托给主构造函数，可以直接委托或者通过别的次构造函数间接委托。委托到同一个类的另一个构造函数用 this 关键字即可：

```kotlin
// 声明了一个次构造函数
class Person {
    constructor(parent: Person) {
        parent.children.add(this)
    }
}
// 有主构造函数时，次构造函数的声明方式
class Person(val name: String) {
    constructor(name: String, parent: Person) : this(name) {
        parent.children.add(this)
    }
}
```

### 2.3 初始化代码块

Kotlin 中也有初始化代码块，非静态初始化代码块使用 `init` 关键字即可。

```kotlin
class Person {
    init { //
        // ...
    }
}
```

初始化块中的代码实际上会成为主构造函数的一部分。委托给主构造函数会作为次构造函数的第一条语句，因此所有初始化块中的代码都会在次构造函数体之前执行。

静态代码块与静态变量定义的方式一致，略显繁琐，后续说明。

### 2.4 函数

函数是 Kotlin 中非常重要的概念，Kotlin 提供了许多便利的函数。

```kotlin
// 默认参数
fun MyFun(a: String = "a", b: String) {
    println("$a $b")
}

// 为 String 增加函数
fun String.lastChar() : Char = this[length - 1]

// 为 String 增加属性
val String.lastChar: Char
    get() = get(length - 1) 

// 可变数量的参数
fun varFun(vararg args: String) { 
    for (arg in args) println(arg)
}

fun main(args: Array<String>) {
    MyFun(a = "x", b = "y")   // 指定参数名称：输出 x y，允许指定参数的名称
    MyFun(b = "y")            // 指定参数名称：输出 a y，使用默认的参数
    val args = arrayOf("A", "B", "C")
    varFun(*args)             // 使用伸展操作符调用可变数量参数的函数
}
```

1. 允许在调用方法的时候指定参数的名称，并且指定了一个参数之后，后面的参数都要指定名称；
2. 允许为函数的参数指定 `缺省参数`，比如上面的 a 默认是 `a`；
3. 可以为别人的函数添加函数和属性，但是 `拓展函数无法访问私有的或者受保护的成员`。本质上拓展函数将调用它的实例当作第一个参数，这是本质的实现原理，很多问题可以依靠这个理解。拓展函数无法被继承，原因很简单，就是因为它们只相当于调用了一个静态方法而把实例当作参数实现的
4. 可变数量参数函数调用的时候可以使用伸展（spread）操作符（就是在数组前面加 `*`）。缺省参数定义的时候需要使用 `vararg`（也许是因为 `..` 被当作其他用途了），当传入数组的时候的需要解包，也就是数组前面加 `*`。
5. 把函数提升到与类同一层次，这样它就成 `静态函数` 了，把字段提升到与类同一层次，这样它就成 `静态字段` 了。
6. 可以在函数内部定义`局部函数`，并且局部函数可以访问外部函数（即闭包）的局部变量。
7. 导入函数的时候可以使用 `as` 重命名导入以简化使用。

### 2.5 属性

#### 2.5.1 声明属性

声明类的属性有 var 和 val 两种方式。声明一个属性的格式是，

```kotlin
var <propertyName>[: <PropertyType>] [= <property_initializer>]
    [<getter>]
    [<setter>]
```

示例程序，

```kotlin
class Person{
    var grade: Int = 0
        get() = field + 1
        set(value) {
            field = value + 1
        }
    var age: Int = 0
        private set     // 修改默认访问权限
}
```

Kotlin 中会将类的局部变量的访问权默认为 pulic 的，所以外部可以直接通过实例获取字段和赋值。可以通过上述方式来修改它的默认方法权限。

可以通过 `get()` 和 `set()` 来重写 getter 和 setter 方法。一般情况下，使用默认的 `get()` 和 `set()` 默认逻辑即可，这也是 Java 规范。如果想增加新的逻辑，可以增加一个新的方法。注意，在覆写的时候，如果要修改属性的值，需要通过 field 来完成。field 标识符只能用在属性的访问器内，也被称为幕后字段。

#### 2.5.2 编译期常量 const

```kotlin
// 定义在类顶层
const val SUBSYSTEM_DEPRECATED: String = "This subsystem is deprecated"

// 定义在类内部，可以用来为类添加静态常量
class MyClass {
    companion object {
        const val EXTRA_LAUNCH_TYPE = "__extra_launch_type"
    } // 外部访问方式是：MyClass.EXTRA_LAUNCH_TYPE
}
```

使用 `const` 修饰符标记为编译期常量。 这些属性需要满足以下要求：

1. 位于`顶层`或者是 `object` 声明或 `companion object` 的一个成员；
2. 以 String 或原生类型值初始化；
3. 没有自定义 getter。

#### 2.5.3 延迟初始化 lateinit 属性与变量

```kotlin
lateinit var subject: TestSubject
```

一般地，属性声明为非空类型必须在构造函数中初始化。当无法在构造器中对属性初始化时，可以用 `lateinit` 修饰该属性。该修饰符只能用于在类体中的属性，而自 Kotlin 1.2 起，也用于顶层属性与局部变量。该属性或变量必须为`非空`类型，并且是`非原生类型`。

在初始化前访问一个 lateinit 属性会抛出一个特定异常，该异常明确标识该属性被访问及它没有初始化的事实。自 1.2 起，可以该属性的引用上使用 `.isInitialized` 检测一个 lateinit var 是否已初始化。

### 2.6 嵌套类与内部类

#### 2.6.1 内部类

下面是 Kotlin 中内部类的使用示例。在这个例子中，声明的内部类类似于 Java 中的非静态内部类，因此进行实例化的时候需要先获取到外部类的实例。

```kotlin
class Outer {
    private val bar: Int = 1
    class Nested {
        fun foo() = 2
    }
    inner class Inner {
        fun foo() = bar // 可以访问外部类变量
    }
}

val demo = Outer.Nested().foo() // == 2
```

类可以标记为 `inner` 以便能够访问外部类的成员。内部类会带有一个对外部类的对象的引用。使用 `inner` 修饰的类属于内部类，没有使用的属于嵌套类。所以，上面的 Nested 属于嵌套类，Inner 属于内部类。但是注意嵌套类和内部类的区别：嵌套类不是内部类，不包含对外部类的引用。所以，比如 Android 中常见的内存泄漏的问题就可以避免了。

#### 2.6.2 匿名内部类

匿名内部类也是我们开发过程中比较常用的定义方式，比如设置点击事件的回调的时候。匿名类的定义又分成下面两种方式：

```kotlin
// 定义一个类
open class A(x: Int) {
    public open val y: Int = x
}

// 使用匿名内部类
window.addMouseListener(object : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) { …… }
    override fun mouseEntered(e: MouseEvent) { …… }
})

// 对函数式接口使用匿名内部类
val listener = ActionListener { println("clicked") }

// 匿名内部类有多个超类的情况
val val ab: A = object : A(1), MouseAdapter {
    override val y = 15
}

// 不适用任何类创建匿名类实例
val adHoc = object {
    var x: Int = 0
    var y: Int = 0
}
```

第一种方式适用于类中包含多个方法的情形，如上面的 MouseAdapter 的匿名类；另一种方式适用于函数式接口，即只有一个方法的接口，如 ActionListener。

如果一个类的超类型有一个构造函数，则必须传递适当的构造函数参数给它。多个超类型可以由跟在冒号后面的逗号分隔的列表指定，如 ab 的定义。

如果不想要明确创建哪种类型，而只是想创建一个匿名类实例，可以按按上面的 `adHoc` 那样定义。

#### 2.6.3 对象表达式

上面是 object 定义匿名类的几个示例，除此之外，它还可以用来定义单例类，

```kotlin
object DataProviderManager {
    // 单例的方法
    fun registerDataProvider(provider: DataProvider) {
        // ……
    }

    // 单例类的属性
    val allDataProviders: Collection<DataProvider>
        get() = // ……
}

// 调用单例类的方法
DataProviderManager.registerDataProvider(……)
```

这种形式定义的单例在初始化的时候是线程安全的，它调用的时候有点类似于 Java 中静态类的方法和属性的调用。这些对象也可以有父类，它的实现方式与普通的类的继承并无二致。

注意：对象声明不能在局部作用域（即直接嵌套在函数内部），但是它们可以嵌套到其他对象声明或非内部类中。

#### 2.6.4 伴生对象

类内部的对象声明可以用 companion 关键字标记的对象是伴生对象，它的使用效果类似于 Java 中的静态字段和静态方法。伴生对象也是可以实现基类和接口的。比如，

```kotlin
class MyClass {
    companion object : Factory<MyClass> {
        override fun create(): MyClass = MyClass()
    }
}

// 调用的方式是：MyClass.create()
```

### 2.7 继承

在 Kotlin 中所有类都有一个共同的超类 Any，类似于 Java 中的 Object，但是两者不同。

Kotlin 中的声明默认都是 `public final` 的，即公共且无法继承，如果希望一个类可以被继承，可以使用 open 关键字进行修饰。覆写函数的时候需要使用 override 关键字进行修饰，并且是必须的。属性的覆盖与函数的覆盖类似，都是使用 override 进行修饰。

```kotlin
// 基类，使用 open 关键字修饰
open class Base(p: Int)

// 继承的时候调用基类的构造器
class Derived(p: Int) : Base(p)

// 当基类有多个构造器的时候
class MyView : View {
    constructor(ctx: Context) : super(ctx)

    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)
}
```

如果派生类有一个主构造函数，其基类型可以（并且必须） 用基类的主构造函数参数就地初始化。

如果派生类没有主构造函数，那么每个次构造函数必须使用 super 关键字初始化其基类型，或委托给另一个构造函数做到这一点。 注意，在这种情况下，不同的次构造函数可以调用基类型的不同的构造函数。

另外，

1. 当在派生类的函数中调用父类函数的时候使用 `super.函数名` 即可，与 Java 一致。
2. 如果实现了多个接口，想要调某个父接口的实现，需要按照 `super<接口>.函数名` 的形式调用。
3. `abstract` 关键字的用法和 Java 一样，它同时具有 open 的语义。
4. **可见性修饰符**：总共有四个，即 private、 protected、 internal 和 public。修饰符 `internal` 表示模块内可见；`protected` 表示子类可见；`private` 表示类内可见，并且子类可见并不代表模块内可见，两个之间没有关系；public 表示没有任何限制，并且是默认级别。
5. 非静态内部类可以使用 `this@外部类名称` 访问外部类的方法和变量。

### 2.8 接口

Kotlin 的接口与 Java 8 类似，既包含抽象方法的声明，也包含实现:                       

```kotlin
interface IClickable {
    fun onClick()
    fun defaulFun() {  // 默认函数，不需要任何声明
        println("I'm defaulFun().")
    }
    fun defaulFun2() {
        println("I'm defaulFun2().")
    }
}
```

### 2.9 数据类

专门用来存储数据的类，在普通类的基础之上使用 data 关键字修饰。系统会自动为我们的数据类生成：equals()、hashCode()、toString()、componentN() 和 copy() 函数的实现。

```kotlin
data class User(val name: String, val age: Int)
```

数据类要求：

1. 主构造函数需要至少有一个参数
2. 主构造函数的所有参数需要标记为 val 或 var
3. 数据类不能是抽象、开放、密封或者内部的
4. （在1.1之前）数据类只能实现接口

上面的 `copy()` 函数类似于 Java 中的 `clone()` 函数，用来实现函数的克隆。                                                                                            
### 2.10 密封类

密封类有点类似于枚举，要声明一个密封类，需要在类名前面添加 sealed 修饰符。虽然密封类也可以有子类，但是所有子类都必须在与密封类自身相同的文件中声明。密封类不允许有非-private 构造函数（其构造函数默认为 private）。

```kotlin
sealed class Expr
data class Const(val number: Double) : Expr()
data class Sum(val e1: Expr, val e2: Expr) : Expr()
object NotANumber : Expr()
```

理解上，密封类的作用是类似于枚举，但是对类的位置进行了限制。这是为了让运用于 when 的子类能够更容易被发现。

### 2.11 枚举类

声明枚举类的时候需要使用 enum 关键字，也可以给枚举增加一些属性，其定义方式基本同 Java. 

```kotlin
enum class City {
    BEIGING, SHANGHAI, GUANGZHOU
}
enum class City2(level:Int) {
    BEIGING(1), SHANGHAI(2), GUANGZHOU(3)
}
```

## 3、高阶特性

### 3.1 Lambda 表达式

#### 3.1.1 Lambda 表达式的基本示例

Lambda 表达式的格式是：`{ x: Int, y: Int -> x + y }`。它的使用比较简单，通常用来定义函数式接口。如果变量的含义明确，它还可以进一步简化，比如 `{ it * it}` 也是可以的。

#### 3.1.2 集合与 Lambda

以下面的程序为例，我们可以在集合中使用 Lambda 表达式。在 Java 8 中，我们可以使用 Stream 进行编程，而 Android 中要求 API 24 以上才能使用 Stream，所以 Kotlin 可以帮助我们解决这个遗憾。

```kotlin
listOf(1,2,3,4).filter { it > 2 }.map { it.toString() }.all { it.length > 2 }
```

它支持的操作符包括：`filter`, `map`, `all`, `any`, `count` 和 `find`, `groupBy`, `flatMap` 和 `flatten`。它们的用法和效果与 Stream 或者 RxJava 中的操作符的含义一致。

### 3.1.3 with 与 apply

`with` 表示以某个类作为开始，对其进行操作，最后返回。`apply` 对应于 with，表示对某个实例进行某种操作；（省去了声明一个实例的过程，仅此而已，但是新添加一个语法……）

它们的效果有点类似于在 Java 中的这种写法。

```java
new LinedList<String>{
    {
        add("A");
        add("B");
    }
}
```

也就是可以为声明的对象增加一些操作，但是这些过程都被包含在了 `with` 和 `apply` 中。参考下面的程序：

```kotlin
// with
fun getString() : String = with(LinkedList<Int>()) {
    for (i in 1..10) {
        this.add(i) // 这里的 this 就是上面传入的列表
    }
    this.toString()
}
// apply
fun getString2() : String = LinkedList<Int>().apply { 
    for (i in 1..10) {
        add(i)
    }
}.toString()
```

### 3.2 区间

与区间相关的几个操作符是 `..`、`in`、`!in`、`until`、`downTo` 以及 `until`。其含义如下，Kotlin 中的区间默认是闭区间的：

```kotlin
    val nums = 1..10
    // 输出结果是 1..10
    println(nums)                   
 
    // 输出结果是 12345678910
    for (num in nums) {             
        print(num)
    }                  
 
    // 输出结果是 1086，10 递减到 5，步进 2
    for (i in 10 downTo 5 step 2) { 
        print(i)
    }                   
 
    // 输出结果是 12345，1 递增到 6，步进 1
    for (i in 1 until 6) {          
        print(i)
    }    
```

### 3.3 集合

Kotlin 中的集合比 Java 中的集合，增加了可变和不可变的概念。不可变集合的好处在于它的线程安全性（估计这个又是从 Guava 中借鉴来的概念）。在创建集合的时候，我们无需按照 Java 中使用 new 的方式来创建。在使用的时候还是要注意区分。下面我们来列举些这些集合，

![不可变集合的创建方法](res/QQ截图20190303112453.png)

![可变集合的创建方法](res/QQ截图20190303112506.png)

Kotlin 中的不可变集合的一个好处是，它本身就不会提供插入和删除的方法，所以无需担心因为该方法没有实现而出现的运行时异常。

上面也说过，Kotlin 中的集合支持 Stream 的一些操作，除了上面的那些，它还支持许多其他的操作，这里就不一一列举了。

### 3.4 类型系统

Kotlin 对空类型的处理比较好：默认所有的参数都是非空的，除非显式声明其可以为空。而 Java 中默认全部都是可空的。这可以有效帮助我们减少程序中的 NPE. 

```kotlin
fun testFun1(param : String) {
    print(param.length)
}

// 如果一个类是可空的那么必须显式声明，所以下面的程序编译器提示错误
fun testFun2(param : String?) {
//    print(param.length)
}

fun main(args : Array<String>) {
//    testFun1(null) // 编译器提示错误
    testFun2(null)
    val str : String? = null
    println(str?.length)            // null
    println(str ?: "B")             // B
    val b = "AA".let { it + "A" }   // AAA
    println(b)
}
```

1. 使用 `?` 在类型的后面则说明这个变量是可空的。
2. 安全调用运算符 `?.`，以 `a?.method()` 为例，当 a 不为 null 则整个表达式的结果是 `a.method()` 否则是 null；
3. Elvis 运算符 `?:`，以 `a ?: "A"` 为例，当 a 不为 null 则整个表达式的结果是 a，否则是 "A"；
4. 安全转换运算符 `as?`，以 `foo as? Type` 为例，当 foo 是 Type 类型则将 foo 转换成 Type 类型的实例，否则返回 null；
5. 非空断言 `!!`，用在某个变量后面表示断言其非空，如 `a!!`；
6. `let` 表示对调用 let 的实例进行某种运算，如 `val b = "AA".let { it + "A" }` 返回 "AAA"。如果使用 let 的某个对象是可空的，那么只有当该对象非空的时候才会执行 let。
7. Kotlin 中进行类型之间的转换的时候必须显式进行，需要调用 `toXX()` 方法；
8. `Any` 和 `Any?` 分别是所有非空和空类型的超类；
9. `Unit` 相当于 Java 中的 void，返回 Unit 就相当于返回 void；

## 4、协程

常规的线程使用时，上下文切换会带来额外的性能开销。线程适用于 CPU 密集型的程序，而协程适合 Android 这种 IO 密集型的程序。从执行效果上面看，协程和线程达到的效果基本一致。它们的区别主要有以下几点：

1. 协程不需要进行同步控制；
2. 可以开大量的协程，但是线程数量是有限的，不然会影响程序的运行时性能；
3. 使用 GlobalScope 启动的协程像守护线程，当程序中的所有线程都结束的时候，整个程序结束，没有执行完毕的协程不会继续执行；

协程配置等相关信息：[kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)

**挂起函数**：使用 suspend 修饰的函数，挂起函数能够以与普通函数相同的方式获取参数和返回值，但是调用函数能挂起协程。挂起函数挂起协程时，不会阻塞协程所在的线程，挂起函数执行完成后会恢复协程。所以，挂起函数只能在协程中或其他挂起函数中调用。

**CoroutineScope 和 CoroutineContext**：CoroutineScope 时协程本身，包含了 CoroutineContext。CoroutineContext，协程上下文，是一些元素的集合，主要包括 Job 和 CoroutineDispatcher 元素，可以代表一个协程的场景。

**CoroutineDispatcher**：协程调度器，决定协程所在的线程或线程池。指定协程运行于特定的一个线程、一个线程池或者不指定任何线程。有三种标准实现 Dispatchers.Default、Dispatchers.IO，Dispatchers.Main和Dispatchers.Unconfined，Unconfined 就是不指定线程。

**构建协程**：`CoroutineScope.launch {}` 不阻塞当前线程，在后台创建一个新协程，也可以指定协程调度器。`runBlocking {}`：创建一个新的协程同时阻塞当前线程，直到协程结束。`withContext {}` 不会创建新的协程，在指定协程上运行挂起代码块，并挂起该协程直至代码块运行完成。`async {}` 在后台创建一个新协程，跟 `CoroutineScope.launch {}` 的区别在于它有返回值。

