/**
 * You received this file as part of an experimental
 * build tool ('makebuilder') - originally developed for MCA2.
 *
 * Copyright (C) 2011 Max Reichardt,
 *   Robotics Research Lab, University of Kaiserslautern
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
//----------------------------------------------------------------------
/*!\file    enum_strings.cpp
 *
 * \author  Max Reichardt
 *
 * \date    2011-08-24
 *
 * \brief Contains methods to retrieve auto-generated string constants
 *        for enum types
 */
//----------------------------------------------------------------------

#include <map>
#include <cxxabi.h>
#include <cstdlib>

namespace make_builder
{
namespace internal
{

/*! lookup typeid (without template parameters) => enum strings */
std::map<std::string, std::vector<const char*>>& GetRegister()
{
  static std::map<std::string, std::vector<const char*>> enum_strings_register;
  return enum_strings_register;
}

const std::vector<const char*>* GetEnumStrings(const char* type_id)
{
  // normalize type_id string
  // demangle...
  int status = 0;
  char* tmp = abi::__cxa_demangle(type_id, 0, 0, &status);
  std::string demangled(tmp);
  free(tmp);

  // remove everything inside and including <>-brackets
  //printf("Pre-remove: %s\n", demangled.c_str());
  size_t pos;
  while ((pos = demangled.find('<')) != std::string::npos)
  {
    size_t cur_pos = pos;
    size_t bracket_count = 1;
    while (bracket_count > 0 && pos < demangled.length())
    {
      cur_pos++;
      char c = demangled[cur_pos];
      if (c == '<')
      {
        bracket_count++;
      }
      else if (c == '>')
      {
        bracket_count--;
      }
    }
    demangled.erase(pos, (cur_pos - pos) + 1);
  }
  //printf("Post-remove: %s\n", demangled.c_str());

  // actual lookup
  size_t count = GetRegister().count(demangled);
  if (count == 1)
  {
    return &(GetRegister()[demangled]);
  }
  return NULL;
}

void RegisterEnumStrings(const char* type_name, const char* const* strings)
{
  assert(GetRegister().count(type_name) == 0 && "may only be initialized once");
  std::vector<const char*> vec;
  for (size_t i = 0;; i++)
  {
    const char* s = strings[i];
    if (s == NULL)
    {
      break;
    }
    vec.push_back(s);
  }
  GetRegister().insert(std::pair<std::string, std::vector<const char*>>(type_name, vec));
}

} // namespace
} // namespace

