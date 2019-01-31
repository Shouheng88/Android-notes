# Kotlin 总结：第一部分

虽然 Kotlin 推出了一段时间了，但是我一直持观望态度。之前一直觉得 Kotlin 只是在 Java 的基础上增加了一些特性，如果本质的性能问题没有提升，那么也没必要深入掌握这种语言。但最近因为开发的时候遇到空指针的判断问题，感觉 Java 的处理方式非常繁琐，所以希望诉诸于 Kotlin. 本次梳理的主要目标在于，

1. 梳理 Kotlin 基本语法和语言设计思路，
2. 了解 Kotlin 的语言特性并分析其实现方式。

## 1、基础特性

1. 本质上是**静态类型语言**，编译期确定类型，**但无需明确指定变量类型**；（！：会不会导致编译过慢？）
2. 对**可空类型的支持**，可以在编译期发现空指针；（Q：如何做到？以什么形式的提示给出？）
3. 支持**函数式编程**，虽然 Java8 以后都支持了；
4. 类文件的后缀名式 `.kt`，编译之后还是生成 class 文件，只是编译器使用的是 `kotlinc`（对应于 javac），执行 class 的时候还是使用 java；
5. 可以使用转换器将 Java 转换成 kotlin；（Q：转换器在哪里？转换的效果怎么样？）
6. 每行后面不需要加分号；
7. Kotlin 标准库给 Java 库做了封装，我们可以简化原生 Java 库的调用；（Q：简化的规则？都有哪些库被简化了？）
8. 数组就是类，没有专门用来声明数组的语法；

## 2、基本结构

### 2.1 类文件结构

在 Kotlin 中文件名称和文件的内容没有关系（在 Java 中文件名和类名相同），并且文件内部定义的是函数还是类都没关系。比如，下面是定义在目录 `me/shouheng/demo1/FirstDemo.kt` 中的类和函数，这里类和函数处于文件的同一层次。另外，一个文件中还可以定义多个类和多个函数，都没有问题。

```kotlin
package me.shouheng

class Person (age : Int, name : String) // 声明了一个类
class Person2 (val age : Int, val name : String) // 声明了一个类
class Person3 (val age : Int, val name : String) { // 声明了一个类
    var married : Boolean = false
    //    get() = married // 不允许会造成递归和栈溢出
    val adult get() = age > 18
}

fun doSomething(person: Person) : Int {
    // 在字符串中使用 “$+变量名” 的格式进行占位，相当于 "My name is" + persion + "!"
    println("My name is $person!")
}

// “if else” 对应于 Java 中的 “? :” 三元运算符
fun max(a : Int, b : Int) : Int = if (a > b) a else b
fun min(a : Int, b : Int) = if (a < b) a else b
```

对于 Kotlin 来说，类和函数的真实包名是由文件中的 **package** 关键字指定的，与文件结构没有必然的关系。当然，我们建议按照 Java 的规则使其对应起来，因为这样维护起来更好、逻辑更清晰些。

上面是 Kotlin 中类和函数的定义的方式，总结下来区别有：

1. 变量和返回值的类型被放在冒号后面；
2. 函数的定义使用关键字 `fun`，覆写的话就在函数名前面加上 `override fun`；
3. 在字符串中使用 `$+变量名` 的格式进行占位（这叫字符串模板），如果希望使用美元符号，前面加上反斜杠即可；
4. Kotlin 中没有 int 等关键字，只有 Int 等包装类；
5. 可以直接像上面的 Person 那样定义类的时候同时声明构造方法；
6. 可以像上面的 min() 和 max() 那样定义表达式函数，此时 `: Int` 可省略，因为类型可以推断；
7. 上面的 Person 和 Person2 有所不同，后者有两个局部变量 `age` 和 `name` 而前者啥局部变量都没有；
8. Kotlin 中会将类的局部变量的访问权默认为 pulic 的，所以外部可以直接通过实例获取字段和赋值；
9. 可以通过 `get()` 和 `set()` 来重写 getter 和 setter 方法，但注意 Kotlin 中的类的字段兼有方法和字段两重含义，重写 `get()` 的时候再调用该字段会不断递归调用，造成栈溢出！（所以，一般情况下，使用默认的 get() 和 set() 逻辑即可，这是符合规范的，如果想增加新的逻辑，可以增加一个新的方法，就像 adult 字段一样。）

### 2.2 程序基本结构

再以下面的程序为例，可以总结如下，

1. 没有专门用来声明数组的，全部都是类，可以像下面这样声明数组，另外，可以按照 `args[0]` 这样获取数组元素；
2. 声明变量有 `var` 和 `val` 两个关键字，系统可以自动推断类型，`var` 声明的变量可以二次赋值，而 `val` 不行，后者相当于 `final` 的；
3. 虽然 `var` 类型的变量可以二次赋值，但是两次赋值的类型必须相同；
4. 可以在声明变量的时候使用冒号指定变量的类型，像下面的 b 一样（大部分情况下可以省略，因为编译器可以自动推断）；
5. 初始化一个类的时候不需要 `new` 关键字（抛出异常的时候自然也一样）；

```kotlin
fun main(args : Array<String>) {
    test(args)
}

fun test(args : Array<String>) {
    val a = Person(10, "Ming")
    var b: Person
    b = Person(11, "Xing")
    doSomething(a)
    doSomething(b)
}
```

### 2.3 枚举

声明枚举的方式如下，也可以给枚举添加一些方法，方式同 Java. 

```kotlin
enum class City {
    BEIGING, SHANGHAI, GUANGZHOU
}
enum class City2(level:Int) {
    BEIGING(1), SHANGHAI(2), GUANGZHOU(3)
}
```

### 2.4 when

类似于 Java 中的 switch，但是它的每个条件中默认加入了 break. 另外，它还有一个比较好的地方是，它会检查枚举是否都包含进去了，如果没全部包含，它会提示你全包含或者加入 else 语句。

```kotlin
// 示例 1
fun multiple(city: City2) = when(city) {
    City2.BEIGING->{
        2*10000
        2+2
    }
    City2.SHANGHAI,City2.GUANGZHOU->3*10000
    else -> 5
}

// 示例 2
interface Expr
class Num(val num : Int) : Expr
class Sum(val a : Int, val b : Int) : Expr
fun testExpr(e : Expr) = when(e) {
    is Num -> e.num
    is Sum -> e.a + e.b
    else -> throw IllegalArgumentException("Invalid e")
}
```

另外，从示例 2 中可以看出，
1. 我们可以直接使用 `is` 来判断类型，类似于 instanceOf，并且判断完之后不需要强转就能直接使用；
2. 可以使用 is 来作为 when 的条件，此外**还可以使用其他的数据类型，when 的条件支持比较宽泛**；
3. 抛出异常的时候**不需要用 new 关键字**；
4. 注意每个条件之后需要加上 `->` 才行哦！
5. 当多个类型的逻辑相同的时候，可以把它们放在 when 的一个条件里，然后用逗号分隔开。

### 2.5 循环

```kotlin
    val nums = 1..10
    println(nums)       // 输出结果是 1..10
    for (num in nums) {
        print(num)
    }                   // 输出结果是 12345678910
    for (i in 10 downTo 5 step 2) {
        print(i)
    }                   // 输出结果是 1086
    for (i in 1 until 6) {
        print(i)
    }                   // 输出结果是 12345
    val map = HashMap<Char, String>()
    for (c in 'A'..'F') {
        map[c] = Integer.toHexString(c.toInt())
    }
    for ((k, v) in map) { // 输出结果是 <A,41> <B,42> <C,43> <D,44> <E,45> <F,46>
        print("<$k,$v> ")
    }
```

1. Kotlin 中的循环与 Java 基本一致；
2. for 循环稍有不同，它跟 js 等更相似；
3. 可以使用 `..` 来得到一个区间，它默认是闭区间的；
4. 第二个循环中应该将 `10 downTo 5 step 2` 理解成一个整体，表示从 10 到 5 每次递减 2；
5. 第三个循环中应该将 `1 until 6` 理解成一个整体，表示从 1 到 6（不包含 6）.
6. 倒数第二个循环之前使用了哈希表，它的赋值方式不需要使用 get 和 set；
7. 遍历 map 的时候使用上述方式，以键值对的形式遍历即可；

### 2.6 异常处理

`try..catch` 语句的基本结构如下，和 Java 基本相似，只是 catch 中声明变量的方式，下面的函数会当小于 0 时返回 -1，否则返回 1. 另外，kotlin 中不分受检异常和非受检异常，不会强制你捕获异常。

```kotlin
fun tryTest(i : Int) = try {
    if (i < 0) throw IllegalArgumentException("< 0")
    else 1
} catch (e : Exception) {
    -1
}
```

## 3、函数

函数是 Kotlin 中非常重要的概念，Kotlin 提供了许多便利的函数。

```kotlin
fun MyFun(a : String = "a", b : String) {
    println("$a $b")
}
fun String.lastChar() : Char = this[length - 1] // 为 String 增加函数
val String.lastChar: Char
    get() = get(length - 1)                     // 为 String 增加属性
fun varFun(vararg args : String) {              // 缺省参数
    for (arg in args) println(arg)
}
fun main(args : Array<String>) {
    MyFun(a = "x", b = "y")         // 输出 x y，允许指定参数的名称
    MyFun(b = "y")                  // 输出 a y，使用默认的参数
    println("ABC".lastChar())       // 使用拓展函数
    println("ABC".lastChar)         // 使用拓展属性
    val args = arrayOf("A", "B", "C")
    varFun(*args)
    val map = mapOf(1.to("A"), 2 to("B"), 3 to "C", 4 to 5) // 中缀
    for ((k, v) in map) {
        print("<$k, $v> ")
    }                               // 输出 <1, A> <2, B> <3, C> <4, 5> 
}
```

1. Kotlin 允许在调用方法的时候指定参数的名称，并且指定了一个参数之后，后面的参数都要指定名称；
2. 允许为函数的参数指定**缺省参数**，比如上面的 a 默认是 `a`；
3. 可以把函数提升到与类同一层次，这样它就成**静态函数**了；
4. 可以把字段提升到与类同一层次，这样它就成**静态字段**了；
5. 可以为别人的函数添加函数和属性，但是**拓展函数无法访问私有的或者受保护的成员**；
6. 导入函数的时候可以**使用 as 重命名导入**以简化使用；
7. 本质上拓展函数将调用它的实例当作第一个参数，这是本质的实现原理，很多问题可以依靠这个解决；
8. **拓展函数无法被继承**，原因很简单，就是因为它们只相当于调用了一个静态方法而把实例当作参数实现的；
9. 缺省参数定义的时候需要使用 `vararg`，也许是因为 `..` 被当作其他用途了，当传入数组的时候的需要解包，也就是数组前面加 `*`；
10. 所谓中缀就是构建一个映射的关系，本质上构建了一个 `Pair<K, V>` 键值对；
11. 如果需要把一个字符串当作正则表达式，需要显式调用字符串的 `toRegex()` 方法才行；
12. 三重引号中的字符不会做任何转义，即 `"""$"""` 可以直接当作美元；
13. **局部函数**就是指函数中定义的函数，可以将其理解成函数中定义一个接口，然后调用该接口。

## 4、类、对象和接口

1. Kotlin 中的声明默认都是 `public final` 的，即公共且无法继承；
2. 嵌套类不是内部类，不包含对外部类的引用；

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
open class Handler1 : IClickable { // 实现接口，并且该类可以被继承
    override fun onClick() {
        println("Handler1#onClick()!")
    }
    override fun defaulFun() {
        super.defaulFun()       // 调用接口的实现
        println("LaLaLa...")
    }
    inner class Innter {
        fun doNothing() {
            this@Handler1.onClick()
        }
    }
}
fun main(args : Array<String>) {
    val handler = Handler1()
    handler.onClick()
    handler.defaulFun()
    handler.defaulFun2()
}
```

3. 跟 Java 一样也只能继承一个类，实现多个接口；
4. 覆写方法的时候使用 override 关键字，且是必须的；
5. 可以给接口增加默认方法，直接加上方法体即可，不需要其他处理；
6. 调用父类方法的时候使用 `super.` 即可；
7. 如果实现了多个接口，想要调某个父接口的实现，需要按照 `super<接口>.方法` 的形式调用；
8. 因为类默认是无法继承的，如果希望它能够被继承，在类前面加上 `open` 关键字即可；
9. `abstract` 关键字的用法和 Java 一样；
10. 另外，修饰符 `internal` 表示模块内可见，`protected` 表示子类可见，`private` 表示类内可见，并且子类可见并不代表模块内可见，两个之间没有关系；
11. 类内定义一个类时，只有当使用 `inner` 修饰的时候它是内部类，否则都属于静态内部类；
12. 非静态内部类可以使用 `this@外部类名称` 访问外部类的方法和变量；
13. 如果一个类使用了 `sealed` 来修饰，那么它的所有子类必须以内部类的形式定义（必须全部罗列在类内部）；

### 4.2 关于构造方法

```kotlin
open class DemoClass constructor(val par1 : String, _par2 : String, val par3 : String = "", _par4 : String) {
    val par2 = _par2
    val par4 : String
    init {
        par4 = _par4
    }
} // 构造方法和各种参数
class DemoClass2 private constructor() {   } // private 的构造方法
class DemoClass3(val par : String) {   } // 构造方法的另一种写法
class DemoClass4(v1: String) : DemoClass(v1, v1, v1, v1) // 覆写的时候
class DemoClass5 : DemoClass {  // 覆写的时候和构造方法的其他写法
    constructor(v : String) : super(v, v, v, v)
    constructor(v : String, v2 : String) : super(v, v, v2, v2)
}
class DemoClass6 {
    var par : Int = 0
        get() = field + 1
        set(value) {
            field = value + 1
        }
    var par2 : Int = 0
        private set     // 修改默认访问权限
}
```

如上，可以
1. 在定义构造方法时指定参数的默认值，如 par3；
2. 在类中直接赋值或者在初始化代码块中赋值，如 par2 和 par4；
3. 在构造方法前面用 `private` 修饰使其成为私有构造方法；
4. 在覆写 set 和 get 的时候使用 field 获取和修改字段的值；
5. 如果父类构造方法中声明了某个变量，子类中如果想要在构造方法中声明同名的变量，则需要加 override 关键字，因为此时属于覆写了；

### 4.3 几个特殊的关键字

声明类的时候几个特殊的关键字（感觉这部分设计的几个关键字考虑的过于繁琐，因为只适用于具体的场景而失去了通用性）

```kotlin
// data 测试
class NormalClass(val name : String, val age : Int)
data class DataClass constructor(val name : String, val age : Int)
// test object
object ObjectClass {
    const val filed = 10
    fun testFun() = print("test fun")
}
// factory
class Instance private constructor(){
    val name = "123"
    companion object {
        fun get() = Instance()
    }
}
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
fun main(args : Array<String>) {
    println(NormalClass("A", 10))   // me.shouheng.chapter3.NormalClass@2de80c
    println(DataClass("A", 10))     // DataClass(name=A, age=10)

    print(ObjectClass.testFun())
    print(ObjectClass.filed)

    println(Instance.get().name)    // 
}
```

1. `data` 表示这个类是数据类，会自动为其生成 `toString()`, `hashCode()` 和 `equals()` 等方法，普通类跟 Java 中的 Object 一样；
2. `object` 声明的类不允许有构造方法，其他和类一样，调用的时候使用类名的方式来调；（给人的感觉有些像类的静态方法和静态字段）
3. `companion` 说明定义的实例是类的内部实例，比如上面可以访问私有构造方法；这种对象叫伴生对象，顾名思义就是伴随外部类生存的……
4. `with` 表示以某个类作为开始，对其进行操作，最后返回；
5. `apply` 对应于 with，表示对某个实例进行某种操作；（省去了声明一个实例的过程，仅此而已，但是新添加一个语法……）

## 5、类型系统

Kotlin 对空类型的处理比较好：默认所有的参数都是非空的，除非显式声明其可以为空。而 Java 中默认全部都是可空的。这可以有效帮助我们减少程序中的 NPE. 

```kotlin
fun testFun1(param : String) {
    print(param.length)
}
fun testFun2(param : String?) {
//    print(param.length) // 编译器提示错误
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

1. 使用 `?` 在类型的后面则说明这个变量是可空的；
2. 安全调用运算符 `?.`，以 `a?.method()` 为例，当 a 不为 null 则整个表达式的结果是 `a.method()` 否则是 null；
3. Elvis 运算符 `?:`，以 `a ?: "A"` 为例，当 a 不为 null 则整个表达式的结果是 a，否则是 "A"；
4. 安全转换运算符 `as?`，以 `foo as? Type` 为例，当 foo 是 Type 类型则将 foo 转换成 Type 类型的实例，否则返回 null；
5. 非空断言 `!!`，用在某个变量后面表示断言其非空，如 `a!!`；
6. `let` 表示对调用 let 的实例进行某种运算，如 `val b = "AA".let { it + "A" }` 返回 "AAA"；
7. 延迟初始化 `lateinit` 表示随后进行初始化；
8. Kotlin 中进行类型之间的转换的时候必须显式进行，需要调用 `toXX()` 方法；
9. `Any` 和 `Any?` 分别是所有非空和空类型的超类；
10. `Unit` 相当于 Java 中的 void，返回 Unit 就相当于返回 void；

## 总结

强化了函数式编程，从类文件结构中可以看出，把函数提升到和类同一高度
使用 Java 中的接口来理解函数式编程即可
