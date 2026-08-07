using System;
using System.Windows.Forms;

namespace CommunicationClient
{
    // WinForms 애플리케이션의 진입점을 제공한다.
    internal static class Program
    {
        // Windows UI 구성 요소는 COM의 STA(Single-Threaded Apartment) 모델에서 실행한다.
        [STAThread]
        private static void Main()
        {
            // 운영체제의 현재 테마를 적용하고 기본 UI 스레드에서 메인 폼을 실행한다.
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }
}
