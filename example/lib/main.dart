import 'dart:async';

import 'package:flutter/material.dart';
import 'package:wifi/wifi.dart';

void main() => runApp(new MyApp());

class MyApp extends StatelessWidget {

  static final Color _caPrimary = Color(0xFF224D47);
  static final MaterialColor caPrimary = MaterialColor(
    _caPrimary.value,
    <int, Color>{
      50: Color(0xFFE4EAE9),
      100: Color(0xFFBDCAC8),
      200: Color(0xFF91A6A3),
      300: Color(0xFF64827E),
      400: Color(0xFF436863),
      500: _caPrimary,
      600: Color(0xFF1E4640),
      700: Color(0xFF193D37),
      800: Color(0xFF14342F),
      900: Color(0xFF0C2520),
    },
  );

  static final Color _caSecondary = Color(0xFF26B0A0);
  static final MaterialColor caSecondary = MaterialColor(
    _caSecondary.value,
    <int, Color>{
      50: Color(0xFFE5F6F4),
      100: Color(0xFFBEE7E3),
      200: Color(0xFF93D8D0),
      300: Color(0xFF67C8BD),
      400: Color(0xFF47BCAE),
      500: _caSecondary,
      600: Color(0xFF22A998),
      700: Color(0xFF1CA08E),
      800: Color(0xFF179784),
      900: Color(0xFF0D8773),
    },
  );

  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      title: 'Wifi',
      theme: new ThemeData(
        primarySwatch: caPrimary,
        colorScheme: ColorScheme.light(
          primary: caPrimary,
          secondary: caSecondary,
        ),
      ),
      home: new MyHomePage(),
    );
  }
}

class MyHomePage extends StatefulWidget {
  @override
  _MyHomePageState createState() => new _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  String _wifiName = 'click button to get wifi ssid.';
  int level = 0;
  String _ip = 'click button to get ip.';
  List<WifiResult> ssidList = [];
  String ssid = '', password = '';

  @override
  void initState() {
    super.initState();
    loadData();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Wi-Fi'),
        centerTitle: true,
      ),
      body: SafeArea(
        child: Scrollbar(
          isAlwaysShown: true,
          child: ListView.builder(
            physics: const BouncingScrollPhysics(),
            itemCount: ssidList.length + 1,
            itemBuilder: (BuildContext context, int index) {
              return itemSSID(index);
            },
          ),
        ),
      ),
    );
  }

  Widget itemSSID(index) {
    if (index == 0) {
      return Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            Row(
              children: <Widget>[
                ElevatedButton(
                  child: Text('ssid'),
                  onPressed: _getWifiName,
                ),
                Offstage(
                  offstage: level == 0,
                  child: Image.asset(level == 0 ? 'images/wifi1.png' : 'images/wifi$level.png', width: 28, height: 21),
                ),
                Text(_wifiName),
              ],
            ),
            Row(
              children: <Widget>[
                ElevatedButton(
                  child: Text('ip'),
                  onPressed: _getIP,
                ),
                Text(_ip),
              ],
            ),
            TextField(
              decoration: InputDecoration(
                border: UnderlineInputBorder(),
                filled: true,
                icon: Icon(Icons.wifi),
                hintText: 'Your wifi ssid',
                labelText: 'ssid',
              ),
              keyboardType: TextInputType.text,
              onChanged: (value) {
                ssid = value;
              },
            ),
            TextField(
              decoration: InputDecoration(
                border: UnderlineInputBorder(),
                filled: true,
                icon: Icon(Icons.lock_outline),
                hintText: 'Your wifi password',
                labelText: 'password',
              ),
              keyboardType: TextInputType.text,
              onChanged: (value) {
                password = value;
              },
            ),
            ElevatedButton(
              child: Text('connection'),
              onPressed: connection,
            ),
          ],
        ),
      );
    } else {
      return Column(children: <Widget>[
        ListTile(
          leading: Image.asset('images/wifi${ssidList[index - 1].level}.png', width: 28, height: 21),
          title: Text(
            ssidList[index - 1].ssid,
          ),
          subtitle: Text(
            ssidList[index - 1].bssid,
          ),
          dense: true,
        ),
        Divider(),
      ]);
    }
  }

  void loadData() async {
    Wifi.list('').then((list) {
      setState(() {
        ssidList = list;
      });
    });
  }

  Future<Null> _getWifiName() async {
    int l = await Wifi.level;
    String wifiName = await Wifi.ssid;
    setState(() {
      level = l;
      _wifiName = wifiName;
    });
  }

  Future<Null> _getIP() async {
    String ip = await Wifi.ip;
    setState(() {
      _ip = ip;
    });
  }

  Future<Null> connection() async {
    Wifi.connection(ssid, password).then((v) {
      print(v);
    });
  }
}
