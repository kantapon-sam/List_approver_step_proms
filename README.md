# List Approver Step PROMs

เริ่มเวอร์ชันที่เผยแพร่บน GitHub ใหม่จาก **1.0.0** โดยเลขรุ่นอ้างอิงจาก `version.properties` ที่เดียว
ดาวน์โหลดได้จาก [GitHub Releases](https://github.com/kantapon-sam/List_approver_step_proms/releases/latest)

โปรแกรม Java 8 / NetBeans สำหรับอ่าน Activity/Milestone → Approval Line แล้วส่งออก CSV
โดยใช้ Selenium และตัวตรวจ/อัปเดต ChromeDriver จาก `ApproveProms_TrueCorp_V3` เป็นฐาน
โปรแกรมคลิกเฉพาะการค้นหา รายละเอียด Step และแท็บ Approval Line ไม่มีฟังก์ชันอนุมัติ/ปฏิเสธงาน

## เริ่มใช้งาน

1. แตก ZIP ของ Release ลงโฟลเดอร์โปรแกรม แล้วดับเบิลคลิก **`List_approver_step_proms.jar`** ด้วย Java 8 ไม่ต้องใช้ CMD
2. โปรแกรมสร้าง `Login.txt`, `input.csv`, `proms.properties` ให้ในครั้งแรก ใส่ User/Password ใน `Login.txt` และรายการใน `input.csv`
3. เลือก CSV แล้วกด **เริ่มอ่านข้อมูล**
4. โปรแกรมเปิด Chrome และกรอก User/Password ให้ จากนั้นกด **Sign in** และทำ OTP/CAPTCHA ด้วยตนเองถ้ามี (รอได้ 5 นาที)
5. ผลลัพธ์อยู่ใน `output/approval_steps_YYYYMMDD_HHMMSS_SSS.csv` ข้าง JAR เปิดด้วย Excel ได้ รองรับภาษาไทย UTF-8 BOM

ใช้ **Java 8 เท่านั้น** ทั้ง build และ runtime พร้อม Google Chrome บน Windows ChromeDriver จะตรวจรุ่น/ดาวน์โหลดอัตโนมัติก่อนเปิด Chrome
โปรแกรมใช้ Chrome session แยกและปิดเฉพาะ session ของตัวเองเมื่อจบงาน
ไฟล์ `Login.txt`, `input.csv`, `proms.properties`, driver และ output จะอ้างจากโฟลเดอร์ JAR เสมอ แม้เปิดจาก working directory อื่น
ปุ่มหยุดจะทำงานหลัง browser operation ที่กำลังรันสิ้นสุด (อาจรอถึง timeout) และบันทึก `CANCELLED` ในไฟล์ที่ทำได้บางส่วน

## Login.txt

```text
User,your_username
Password,your_password
```

ไฟล์ Login มีเฉพาะ `User` และ `Password` ไม่ต้องใส่ค่าอื่น
รหัสผ่านที่มี comma หรือช่องว่างจะคงค่าเดิม ตรวจรูปแบบก่อนเปิด Chrome และไม่แสดงค่าบัญชีใน log หรือ CSV
หากไม่มีไฟล์หรือเว้นทั้งสองค่าว่าง จะให้กรอกบนหน้าเว็บเอง หากใส่เพียงค่าเดียวจะแจ้งให้แก้ไขก่อนเริ่ม
ไฟล์นี้เป็นข้อความธรรมดาสำหรับบัญชีของผู้ใช้ เก็บไว้นอก Git; การ build ไม่เขียนทับไฟล์ที่มีอยู่

## อัปเดตโปรแกรม

- เมื่อเปิดโปรแกรมและทุก 30 นาที โปรแกรมตรวจ GitHub Release ใน background; ใช้งานต่อได้แม้ตรวจรุ่นไม่ได้
- หน้าต่างแสดงรุ่นปัจจุบัน เมื่อมีรุ่นใหม่จะแสดงเลขรุ่นใหม่และปุ่ม **อัปเดตและเปิดใหม่** หรือกด **ตรวจเวอร์ชัน** ได้เอง
- ต้องจบงานอ่าน PROMs ก่อนกดอัปเดต โปรแกรมดาวน์โหลด ZIP ตรวจ SHA-256 และ Java 8 แล้วปิดตัวเอง
- ตัวติดตั้ง Java 8 แยก process จะรอโปรแกรมปิด สำรองไฟล์ ติดตั้ง แล้วเปิด JAR รุ่นใหม่ให้ โดยไม่ใช้ CMD
- หากการแทนไฟล์ล้มเหลว จะพยายามคืนไฟล์รุ่นเดิม และเก็บข้อมูลกู้คืน/บันทึกไว้ใน `.updates`
- `Login.txt`, `input.csv`, `proms.properties`, `output` และ ChromeDriver ไม่อยู่ในรายการไฟล์ที่ updater แก้ไข
- แพ็กเกจ Release และ source ไม่มีบัญชี/รหัสผ่านจริง มีเฉพาะ `Login.example.txt` ที่เว้นค่าทั้งสองช่องว่าง

Feed ใช้ [ลิงก์ดาวน์โหลด asset ของ Release ล่าสุด](https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases)
ชื่อ `update.properties` จาก repository นี้โดยตรง จึงไม่ต้องแจก token GitHub ให้ผู้ใช้

## Input CSV

```csv
Ref No,Project ID,Steps
26070892,,
26070892,,TK07
,240258,TK07;TK08
```

ตัวอย่างด้านบนแสดง 3 รูปแบบ เลือกใช้เฉพาะแถวที่ต้องการเพื่อไม่ให้ได้ผลซ้ำ

- `Ref No`: ค้นเลข Request No./Ref No. แบบตรงตัว
- `Project ID`: รวบรวมทุก Request ที่มี Project ID ตรงกันจากทุกหน้าของรายการที่ตั้งค่าไว้ รองรับคอลัมน์ Project ID หรือรูปแบบ `240258 : ชื่อโครงการ` ใน Project Name
- หากกรอกทั้งสองช่อง จะต้องตรงทั้ง Ref No และ Project ID
- `Steps`: เว้นว่างหรือ `*` เพื่ออ่านทุก Step; ใช้ `TK07` หรือชื่อเต็ม `NT_IE_TK07`; หลาย Step คั่นด้วย `;` หรือ `|`
- ID เก็บเป็นข้อความ ตั้งคอลัมน์ใน Excel เป็น Text ก่อนกรอก หากมีเลขศูนย์นำหน้า
- ต้องมี header; รองรับชื่อ `RefNo`, `Request No.`, `ProjectID`, `Step` ด้วย
- ไม่รับคอลัมน์ไม่รู้จัก จำนวนช่องไม่ครบ หรือ CSV ที่ quote ผิดรูปแบบ โดยตรวจทั้งหมดก่อนเปิด Chrome

## Output CSV

หนึ่งแถวต่อผู้อนุมัติต่อ Step พร้อม `Input Row`, `Input Ref No`, `Input Project ID`, `Ref No`,
`Project ID`, `Project Name`, `Request Type`, `Requester`, `Step`, `Description`, `Approval Order`,
`Approver`, `Status`, `Approved Date`, `Result`, `Message`, `Source URL`, `Extracted At`

`Approved Date` เก็บตามข้อความต้นฉบับ ไม่แปลง พ.ศ./ค.ศ. หรือ timezone หากยังว่างจะคงว่าง
`Approval Order` คืออันดับแถวที่อ่านจากตารางขณะส่งออก ไม่อนุมานเป็นลำดับสิทธิ์การอนุมัติ
`Extracted At` ใช้เวลา Asia/Bangkok ส่วน `Source URL` เป็นหน้ารายการ เพราะรายละเอียดอาจเปิดใน modal บน URL เดิม
ข้อความที่ Excel อาจตีความเป็นสูตรจะเติม `'` นำหน้า ไม่มีการเขียนทับไฟล์รายงานเดิม และ flush ทุกแถว

| Result | ความหมาย |
| --- | --- |
| `OK` | อ่านข้อมูลผู้อนุมัติสำเร็จ |
| `NO_APPROVAL_ROWS` | พบตาราง Approval Line แต่ตารางไม่มีแถวข้อมูล |
| `NOT_FOUND_IN_SCOPE` | ไม่พบ ID ตรงตัวในหน้ารายการที่ตั้งค่าไว้ |
| `STEP_NOT_FOUND` | ไม่พบ Step ที่ระบุ บันทึกแยกสำหรับแต่ละ Step ที่ขาด |
| `NO_STEPS` | พบตาราง Activity/Milestone แต่ไม่มี Step |
| `READ_ERROR` | อ่านรายการ/Step ไม่สำเร็จ ดู Message; โปรแกรมทำรายการถัดไปต่อ |
| `RUN_ERROR` / `CANCELLED` | งานหยุดก่อนเสร็จ ไฟล์อาจมีข้อมูลเพียงบางส่วน |

## ขอบเขตและสถานะการยืนยัน

ค่าเริ่มต้นค้นที่ `https://proms.truecorp.co.th/proms/pendingapprove` ตามโปรเจกต์อ้างอิงและภาพตัวอย่าง
**Pending Approval ไม่ใช่รายการงานทั้งหมดของ PROMs** ผลจึงจำกัดด้วยสิทธิ์บัญชีและรายการที่หน้าเว็บนี้แสดง
ข้อมูลที่อนุมัติเสร็จแล้วหรือไม่อยู่ในคิวอาจค้นไม่พบ ไม่ควรตีความว่าไม่มีโครงการนั้นในระบบ

สามารถเปลี่ยน `list.url` และ `search.css` ใน `proms.properties` ไปยังหน้ารายการ PROMs ที่ตรวจสอบแล้วว่าใช้
คอลัมน์และรายละเอียดแบบเดียวกันได้ แต่ยังไม่ได้ยืนยัน workflow ของหน้า Project/Work Order อื่น

เวอร์ชันนี้พัฒนาจาก source ของโปรเจกต์เดิมและภาพตัวอย่าง ยังไม่ได้ยืนยันกับรายการจริง `26070892`
เพราะเครื่องมือควบคุม Chrome ไม่สามารถยืนยัน URL ของหน้าต่าง Windows และหยุดการตรวจหน้าเว็บ
การค้น/เปิด Activity มีหลักฐานจาก source เดิม ส่วนการจับตาราง Approval Line ใช้ชื่อหัวคอลัมน์
`Approver`, `Status`, `Approved Date` ตามคำขอ ต้องยืนยันกับ DOM จริงเมื่อทดสอบหลัง login
หากหัวคอลัมน์หรือ pagination ไม่ตรง โปรแกรมจะรายงานข้อผิดพลาดแทนการเดาตำแหน่งคอลัมน์

## CLI

รันจากโฟลเดอร์ `dist`:

```text
java -jar List_approver_step_proms.jar --check-input --input input.csv
java -jar List_approver_step_proms.jar --check-login
java -jar List_approver_step_proms.jar --check-update
java -jar List_approver_step_proms.jar --input input.csv --output output/my-report.csv
java -jar List_approver_step_proms.jar --check-driver
java -jar List_approver_step_proms.jar --version
```

Exit code: `0` เสร็จสมบูรณ์, `1` งานล้มเหลว, `2` มีแถวที่ต้องตรวจสอบ, `130` ผู้ใช้หยุดงาน
หากรันจาก NetBeans ค่า input/config/output จะอ้างจาก project working directory

## Build และทดสอบ

เปิดโปรเจกต์นี้ใน NetBeans โดยเลือก JDK 8 หรือรันสคริปต์สำหรับนักพัฒนา:

```powershell
./scripts/Build.ps1 -JavaHome 'C:\path\to\jdk8'
./scripts/Package-Release.ps1
```

สคริปต์ดาวน์โหลด Ant และ dependencies ตาม URL/ตรวจ SHA-256 ใน `dependencies.lock.json` แล้วรัน `ant test jar` ด้วย **JDK 8 เท่านั้น**
build จะปฏิเสธ JDK เวอร์ชันอื่น และ runtime จะปฏิเสธ Java เวอร์ชันอื่น
ใช้ `scripts/Prepare-Libraries.ps1` เมื่อต้องตั้งค่า libraries ให้ NetBeans แยกต่างหาก
ชุด `ant test` เป็นการทดสอบ offline: CSV quoting/BOM/ภาษาไทย, exact matching, Project ID expansion,
all/selected steps, pending dates, error continuation, cancellation, Login.txt parsing, driver update/recovery
และ updater รวมทดสอบ process ติดตั้ง/เปิดใหม่จริงด้วยโปรแกรมจำลอง การ rollback และการคงข้อมูลผู้ใช้
ไม่มีการเชื่อมต่อหรือเปลี่ยนแปลงข้อมูลใน PROMs

ทดสอบ DOM ด้วย Chrome แบบ headless บน localhost fixture (ข้อมูลสมมติเท่านั้น):

```text
ant test-fixture -Dfixture.driver="C:\path\to\chromedriver.exe"
```

fixture ตรวจการกรอก User/Password ด้วยบัญชีสมมติและรอให้ผู้ใช้กด Sign in, การแบ่งหน้าทั้ง Request/Step/Approval, การโหลดข้อมูลแบบ asynchronous,
การสลับ Step โดยไม่อ่าน modal เก่า, วันที่ว่าง และการปฏิเสธตารางที่หัวคอลัมน์เปลี่ยน
การทดสอบนี้ผ่านแล้วบนเครื่องนี้ แต่ไม่ทดแทนการทดสอบกับ DOM ของ PROMs จริง

## ออก Release รุ่นถัดไป

1. แก้ source พร้อมทดสอบ แล้วเพิ่มเลข `app.version` ใน `version.properties` เช่น `1.0.1` และบันทึก CHANGELOG
2. Commit และ push เข้า `main`; GitHub Actions จะ build/test ด้วย Java 8 แล้วออก tag/Release และแนบ ZIP, `update.properties`, `SHA256SUMS.txt` อัตโนมัติ
3. Workflow อัปโหลดไฟล์เข้า draft ให้ครบก่อนเผยแพร่เป็น latest; รุ่นที่เผยแพร่แล้วจะไม่ถูกเขียนทับ ให้เพิ่มเลขรุ่นใหม่เสมอ
4. ผู้ใช้จะเห็นรุ่นใหม่จาก feed เดียวกันและกดอัปเดตได้

สั่งเผยแพร่ด้วยตนเองหลัง commit/build ได้ผ่าน `scripts/Publish-Release.ps1` โดยใช้ GitHub CLI ที่ยืนยันตัวตนแล้วหรือ Git credential helper ของเครื่อง
ไม่มีการบันทึก token ลงไฟล์ การตรวจ credential helper ใช้ข้อมูลใน memory เฉพาะ process ที่เผยแพร่

ไฟล์ `input.csv`, `Login.txt`, `output`, `.updates`, `build`, `dist`, `release`, driver และ binary libraries ถูกกันออกจาก Git
การจัด ZIP ใช้รายการไฟล์ที่อนุญาตโดยตรง ไม่รวมโฟลเดอร์ dist ทั้งก้อน และตรวจว่า template Login ภายนอก/ภายใน JAR เป็นค่าว่าง
