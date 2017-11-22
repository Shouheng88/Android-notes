在jdk 7之前，switch 只能支持 byte、short、char、int 这几个基本数据类型和其对应的封装类型。switch后面的括号里面只能放int类型的值，但由于byte，short，char类型，它们会自动转换为int类型（精精度小的向大的转化），所以它们也支持。

注意，对于精度比int大的类型，比如long、float，doulble，不会自动转换为int，如果想使用，就必须强转为int，如(int)float;

jdk1.7后，整形，枚举类型，boolean，字符串都可以。

其实，jdk1.7并没有新的指令来处理switch string，而是通过调用switch中string.hashCode,将string转换为int从而进行判断。