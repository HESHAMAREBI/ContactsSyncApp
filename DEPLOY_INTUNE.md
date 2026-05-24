نشر وتصدير تطبيق Jumhoria Contacts لإدارة عبر Microsoft Intune

الخطوات التالية تشرح كيفية إنشاء حزمة إصدار (APK أو AAB)، توقيعها، ثم رفعها إلى Microsoft Intune لإدارة توزيعها على أجهزة المؤسسة.

1) التحضير
- تأكد من أن المشروع يُبنى بنجاح (Build -> Make Project).
- حدّث نسخة التطبيق والـ versionCode في `app/build.gradle.kts` (أو `build.gradle`) حسب الحاجة.
- تأكد من أن `AndroidManifest.xml` يحتوي `android:label` الصحيح و `android:icon="@mipmap/ic_launcher"`.

2) إنشاء مفتاح التوقيع (keystore)
- سيتم استخدام keystore لتوقيع الحزمة. يمكنك إنشاء واحد محلياً:

  windows (PowerShell):

  keytool -genkey -v -keystore jumhoria-release.jks -alias jumhoria-key -keyalg RSA -keysize 2048 -validity 10000

- احتفظ بالـ keystore والبيانات بأمان. ستحتاج إلى هذا لاحقاً عند تحديث التطبيق.

3) توليد App Bundle (AAB) أو APK مُوقَّع
- في Android Studio: Build -> Generate Signed Bundle / APK -> اختر AAB (مفضل للنشر عبر Google Play) أو APK.
- اتبع المعالج، اختر `jumhoria-release.jks`، أدخل كلمة المرور، اختر alias.
- اختر Release build type ثم Finish.
- الناتج سيظهر في: `app/build/outputs/apk/release/` أو `app/build/outputs/bundle/release/`.

4) اختبار الحزمة الموقعة محلياً
- لفحص APK: انسخ APK إلى جهاز اختبار وقم بتثبيته يدوياً للتأكد من أنه يعمل ويحمل الأيقونة والاسم الصحيح.

5) رفع الحزمة إلى Microsoft Intune (Endpoint Manager)
- تسجيل الدخول إلى Microsoft Endpoint Manager admin center: https://endpoint.microsoft.com
- من لوحة التحكم: Apps -> Android -> Add -> Line-of-business app
- اختر الملف APK الموقّع أو AAB، ارفع الملف.
- املأ البيانات (اسم التطبيق، مُعَرِّف الحزمة packageName الذي يوجد في `AndroidManifest.xml`, الوصف، الإعلانات، الفئة).
- إعداد Assignment: حدد المستخدمين أو مجموعات الأجهزة المستهدفة لنشر التطبيق.
- اختر قواعد النشر (متاحة للتثبيت التلقائي أو متاحة ضمن Company Portal للمستخدمين).

6) سياسات الأذونات وConditional Access
- أنشئ سياسة لمنع المستخدمين من تعطيل مزامنة جهات الاتصال إذا رغبت المؤسسة بذلك (اعتمادًا على إمكانيات Intune وAndroid Enterprise).

7) تحديث التطبيق لاحقًا
- عند ترقية التطبيق: استخدم نفس keystore (jumhoria-release.jks) ورفعه كنسخة محدثة في Intune بنفس packageName و higher versionCode.

نصائح إضافية
- استخدم AAB إن كان متاحًا (Google Play)، لكن Intune عادة يقبل APK لـ LOB apps.
- احفظ keystore في مكان آمن، وفكّر باستخدام Key Vault أو نظام إدارة أسرار للمؤسسات.

انتهى
