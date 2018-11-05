今天在使用URL访问别人的接口的时候，因为要查询的数据中包含中文就出现了问题。

    http://api.map.baidu.com/telematics/v3/weather?location=北京&output=json&ak=XXXX

使用

    URL url = new URL(address);
    connection = (HttpURLConnection) url.openConnection();

来打开数据连接，但是始终获取不到数据。这是因为要访问的URL中包含中文“北京”，因而无法查询。
如何修改这个问题呢？

实际上，将上面的链接复制到浏览器进行访问是没有问题的。这时，如果细心的话就会发现，粘贴
之后被访问的连接并非粘贴过去的那串文字，其中的“北京”两个字变成了

    http://api.map.baidu.com/telematics/v3/weather?location=%E5%8C%97%E4%BA%AC&output=json&ak=XXXX

就是将“北京”连个字做了变化。所以，要想使用中文的URL的话，也应该将其中的中文进行转码之后再访问。

那么如何转码呢？

    String city = mEditText.getText().toString();
    try {
        city = URLEncoder.encode(city, "utf-8");
    } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
    }

使用ERLEncoder.encode即可。这样中文就变成上面那串带有%的字符串了。
