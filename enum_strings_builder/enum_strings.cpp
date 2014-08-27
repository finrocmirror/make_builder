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

//----------------------------------------------------------------------
// External includes (system with <>, local with "")
//----------------------------------------------------------------------
#include <map>
#include <cxxabi.h>
#include <cstdlib>
#include <iostream>
#include <sstream>

//----------------------------------------------------------------------
// Debugging
//----------------------------------------------------------------------

//----------------------------------------------------------------------
// Namespace usage
//----------------------------------------------------------------------

//----------------------------------------------------------------------
// Namespace declaration
//----------------------------------------------------------------------
namespace make_builder
{

//----------------------------------------------------------------------
// Forward declarations / typedefs / enums
//----------------------------------------------------------------------
typedef std::map<std::string, const internal::tEnumStrings * const> tEnumStringsRegister;

//----------------------------------------------------------------------
// Const values
//----------------------------------------------------------------------

//----------------------------------------------------------------------
// Implementation
//----------------------------------------------------------------------

namespace internal
{

//----------------------------------------------------------------------
// EnumStringsRegister
//----------------------------------------------------------------------
tEnumStringsRegister &EnumStringsRegister()
{
  static tEnumStringsRegister enum_strings_register;
  return enum_strings_register;
}

//----------------------------------------------------------------------
// RegisterEnumStrings
//----------------------------------------------------------------------
void RegisterEnumStrings(const char *type_name, const internal::tEnumStrings &strings)
{
  assert(EnumStringsRegister().count(type_name) == 0 && "may only be initialized once");
  EnumStringsRegister().insert(std::make_pair(type_name, &strings));
}

//----------------------------------------------------------------------
// GetEnumStrings
//----------------------------------------------------------------------
const tEnumStrings &GetEnumStrings(const char *type_name)
{
  // normalize type_id string
  // demangle...
  int status = 0;
  char* tmp = abi::__cxa_demangle(type_name, 0, 0, &status);
  std::string demangled(tmp);
  free(tmp);

  // remove everything inside and including <>-brackets
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

  // actual lookup
  if (EnumStringsRegister().count(demangled) != 1)
  {
    std::stringstream message;
    message << "Could not find enum strings for type_id: '" << demangled.c_str() << "'\nCandidates are:\n\n";
    for (auto it = EnumStringsRegister().begin(); it != EnumStringsRegister().end(); ++it)
    {
      message << "\t" << it->first.c_str() << "\n";
    }

    message << "\nPlease note that enum strings are only generated for public enums in .h files.\n";
    throw std::runtime_error(message.str());
  }

  return *EnumStringsRegister()[demangled];
}

}

//----------------------------------------------------------------------
// tRegisterEnumStrings constructors
//----------------------------------------------------------------------
internal::tRegisterEnumStrings::tRegisterEnumStrings(const char* type_name, const tEnumStrings &strings)
{
  RegisterEnumStrings(type_name, strings);
}

//----------------------------------------------------------------------
// End of namespace declaration
//----------------------------------------------------------------------
}
