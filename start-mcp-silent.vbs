Set sh = CreateObject("WScript.Shell")
bat = "E:\pro\orange-wz\start-mcp.bat"
' 0 = hidden window, False = do not wait
sh.Run "cmd /c """ & bat & """", 0, False