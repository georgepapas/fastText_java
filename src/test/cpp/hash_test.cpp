#include <stdint.h>
#include <iostream>
#include <string>
#include <vector>

class TestClass {
public:
  TestClass();
  uint32_t hash(const std::string& str) const;
};

TestClass::TestClass() {
}

uint32_t TestClass::hash(const std::string& str) const
{
  uint32_t h = 2166136261;
  for (size_t i = 0; i < str.size(); i++) {
    h = h ^ uint32_t(str[i]);
    h = h * 16777619;
  }
  return h;
}

int main()
{
  const TestClass tester;

  std::vector<std::string> test;
  test.push_back("");
  test.push_back("a");
  test.push_back("Test");
  test.push_back("This is some test sentence.");
  test.push_back("这是一些测试句子。");
  test.push_back("Šis ir daži pārbaudes teikumi.");
  test.push_back("Тестовое предложение");
  test.push_back("Получение положительного заключения испытательной лаборатории по результатам сертификационных испытаний ИСУ ОПК на соответствие требованиям информационной безопасности.");
  test.push_back("梁文博");
  test.push_back("சுள்ளி");
  test.push_back("豐胸秘cup");
  test.push_back("يال");
  test.push_back("センコロール");
  test.push_back("عليسرا‎");
  test.push_back("కత్తి");
  test.push_back("珍寶王國");


  for (int i = 0; i < test.size(); ++i) {
    std::string w = test[i];
    uint32_t h = tester.hash(w);
    std::cout << "" << h << "\t'" << w << "'\n";
  }
  return 0;
}