//
// You received this file as part of an experimental
// build tool ('makebuilder')
//
// Copyright (C) Finroc GbR (finroc.org)
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//----------------------------------------------------------------------
/*!\file    enum_strings/clang-plugin-enum_strings.cpp
 *
 * \author  Max Reichardt
 *
 * \date    2014-01-04
 *
 * Plugin for llvm-clang that generates string constants for all public enums
 * that can be retrieved at runtime.
 */
//----------------------------------------------------------------------

#include <fstream>
#include <sstream>
#include <iostream>
#include <limits>
#include <set>
#include <clang/AST/AST.h>
#include <clang/AST/ASTConsumer.h>
#include <clang/Frontend/FrontendPluginRegistry.h>

class GenerateEnumStringsAction : public clang::PluginASTAction
{
public:
  std::string output_file;
  std::vector<std::string> input_files;
  std::string include_dir;

#if __clang_major__ == 3 && __clang_minor__ < 6
  virtual clang::ASTConsumer *CreateASTConsumer(clang::CompilerInstance &CI, llvm::StringRef InFile) override;
#else
  virtual std::unique_ptr<clang::ASTConsumer> CreateASTConsumer(clang::CompilerInstance &CI, llvm::StringRef InFile) override;
#endif

  virtual bool ParseArgs(const clang::CompilerInstance &CI, const std::vector<std::string>& args) override
  {
    const std::string OUTPUT_PREFIX = "--output=";
    const std::string INPUTS_PREFIX = "--inputs=";
    const std::string INCLUDE_PREFIX = "--include_dir=";
    for (std::vector<std::string>::const_iterator it = args.begin(); it != args.end(); ++it)
    {
      if ((*it).substr(0, OUTPUT_PREFIX.length()) == OUTPUT_PREFIX)
      {
        output_file = (*it).substr(OUTPUT_PREFIX.length());
      }
      if ((*it).substr(0, INCLUDE_PREFIX.length()) == INCLUDE_PREFIX)
      {
        include_dir = (*it).substr(INCLUDE_PREFIX.length()) + "/";
      }
      if ((*it).substr(0, INPUTS_PREFIX.length()) == INPUTS_PREFIX)
      {
        std::string files = (*it).substr(INPUTS_PREFIX.length());
        //llvm::outs() << "\nInput Files: " << files << "\n";
        std::stringstream stream(files);
        std::string file;
        while (std::getline(stream, file, ' '))
        {
          input_files.push_back(file);
        }
      }
    }
    return true;
  }
};

enum StringFormatting
{
  eSF_NATURAL,
  eSF_UPPER,
  eSF_LOWER,
  eSF_CAMEL,
  eSF_DIMENSION
};

std::string FormatEnumString(const std::string& s, StringFormatting formatting)
{
  std::string result = s;
  switch (formatting)
  {
  case eSF_UPPER:
    std::transform(result.begin(), result.end(), result.begin(), ::toupper);
    break;
  case eSF_LOWER:
    std::transform(result.begin(), result.end(), result.begin(), ::tolower);
    break;
  default:
    break;
  case eSF_NATURAL:
  case eSF_CAMEL:
    std::transform(result.begin(), result.end(), result.begin(), ::tolower);
    // split string
    std::vector<std::string> words;
    std::stringstream stream(result);
    std::string word;
    while (std::getline(stream, word, '_'))
    {
      words.push_back(word);
    }

    // transform first letters to upper case
    for (size_t i = 0; i < words.size(); i++)
    {
      words[i][0] = toupper(words[i][0]);
    }

    // join
    std::ostringstream join_stream; // reset stream
    for (size_t i = 0; i < words.size(); i++)
    {
      if (i > 0 && formatting == eSF_NATURAL)
      {
        join_stream << " ";
      }
      join_stream << words[i];
    }
    result = join_stream.str();
    break;
  }
  return result;
}

std::string CreateStringArrayVarName(const std::string& qualified_name, StringFormatting formatting)
{
  std::stringstream result;
  result << "strings_";
  for (size_t i = 0; i < qualified_name.length(); i++)
  {
    char c = qualified_name[i];
    result << ((c == ':') ? '_' : c);  // replace colons with underscores
  }
  const char* suffixes[5] = { "_natural", "_upper", "_lower", "_camel", "" };
  result << suffixes[formatting];
  return result.str();
}

class GenerateEnumStringsConsumer : public clang::ASTConsumer
{
  clang::ASTContext* ast_context;
  GenerateEnumStringsAction& action;
  std::ostringstream generated_code;
  std::set<std::string> includes;

public:

  GenerateEnumStringsConsumer(GenerateEnumStringsAction& action) :
    ast_context(NULL),
    action(action),
    generated_code()
  {}

  virtual void Initialize(clang::ASTContext &Context)
  {
    ast_context = &Context;
  }

  virtual bool HandleTopLevelDecl(clang::DeclGroupRef DG)
  {
    for (clang::DeclGroupRef::iterator i = DG.begin(), e = DG.end(); i != e; ++i)
    {
      const clang::Decl *D = *i;
      //if (const clang::NamedDecl *ND = dynamic_cast<const clang::NamedDecl*>(D))
      //llvm::outs() << "top-level-decl: \"" << ND->getNameAsString() << "\"\n";
    }

    return true;
  }

  virtual void HandleInterestingDecl(clang::DeclGroupRef DG)
  {
    for (clang::DeclGroupRef::iterator i = DG.begin(), e = DG.end(); i != e; ++i)
    {
      //const clang::Decl *D = *i;
      //if (const clang::NamedDecl *ND = dynamic_cast<const clang::NamedDecl*>(D))
      //llvm::outs() << "top-level-decl: \"" << ND->getNameAsString() << "\"\n";
      (*i)->print(llvm::outs());
    }
  }

  virtual void HandleTagDeclDefinition(clang::TagDecl *D) override
  {
    //const clang::Decl *D = *i;
    //if (const clang::NamedDecl *ND = dynamic_cast<const clang::NamedDecl*>(D))
    //llvm::outs() << "top-level-decl: \"" << ND->getNameAsString() << "\"\n";
    if (D->isEnum() && D->getName().size() > 0)
    {
      //D->getOuterLocStart().print(llvm::outs(), ast_context->getSourceManager());
      std::string filename;
      if (D->getOuterLocStart().isFileID())
      {
        filename = ast_context->getSourceManager().getFilename(D->getOuterLocStart());
        if (filename.length() > 2 && filename[0] == '.' && filename[1] == '/')
        {
          filename = filename.substr(2);
        }
      }
      clang::EnumDecl* enum_decl = dynamic_cast<clang::EnumDecl*>(D);
      if (enum_decl && filename.length() > 0 && filename[0] != '/' &&
          std::find(action.input_files.begin(), action.input_files.end(), filename) != action.input_files.end() &&
          enum_decl->getAccess() != clang::AS_private && enum_decl->getAccess() != clang::AS_protected)
      {
        //llvm::outs() << "\nNext Enum: " << filename << " " << D->getQualifiedNameAsString() << "\n";
        //D->print(llvm::outs());
        //llvm::outs() << "\n";

        // TODO: should we check if enum type names conform to Finroc coding conventions here? (done in perl version, but rather rrlib/finroc-specific)

        // Copy enum constants to std::vector
        std::vector<std::string> constants;
        for (clang::EnumDecl::enumerator_iterator it = enum_decl->enumerator_begin(); it != enum_decl->enumerator_end(); ++it)
        {
          constants.push_back((*it)->getNameAsString());
        }

        // Get common enum prefix
        size_t prefix_length = 0;
        if (constants.size() == 1)
        {
          prefix_length = (constants[0].length() && constants[0][0] == 'e') ? 1 : 0;
        }
        else if (constants.size() > 1 && constants[0].length() && constants[0][0] == 'e')
        {
          prefix_length = std::numeric_limits<size_t>::max();
          for (size_t i = 0; i < constants.size(); i++)
          {
            prefix_length = std::min(constants[i].length() - 1, prefix_length);
          }
          std::string first_name = constants[0];
          for (size_t i = 1; i < constants.size(); i++)
          {
            while (first_name.compare(0, prefix_length, constants[i], 0, prefix_length) != 0 && prefix_length)
            {
              prefix_length--;
            }
          }
        }

        // Remove last constant if it is called DIMENSION (TODO: rather rrlib/finroc-specific)
        if (constants.size() && constants.back().substr(prefix_length) == "DIMENSION")
        {
          constants.pop_back();
        }

        // Do we have an array with standard constants?
        bool standard_constants = true;
        bool init_expression_found = false;
        bool in_template = enum_decl->getDeclContext() && enum_decl->getDeclContext()->isDependentContext();
        bool all_zero = true;
        size_t current_index = 0;
        for (clang::EnumDecl::enumerator_iterator it = enum_decl->enumerator_begin(); current_index < constants.size(); ++it)
        {
          //llvm::outs() << D->getQualifiedNameAsString() << "[" << current_index << "] " << (*it)->getInitVal() << " " << (*it)->getInitExpr() << " " << in_template << "\n";
          standard_constants &= ((*it)->getInitVal() == current_index);
          all_zero &= ((*it)->getInitVal() == 0);
          init_expression_found |= ((*it)->getInitExpr() != NULL);
          current_index++;
        }
        if (in_template && init_expression_found)
        {
          llvm::outs() << "enum-strings-builder warning: Enums in templates with custom (non-standard) values are not supported (" << D->getQualifiedNameAsString() << "). Skipping.\n";
          return;
        }
        if ((!standard_constants) && (!in_template) && all_zero)
        {
          llvm::outs() << "enum-strings-builder warning: All enum values are zero (" << D->getQualifiedNameAsString() << "). Skipping\n";
          return;
        }
        if ((!standard_constants) && in_template)
        {
          standard_constants = true;
        }

        // Generate code
        for (int i = 0; i < eSF_DIMENSION; i++)
        {
          generated_code << "const char* " << CreateStringArrayVarName(D->getQualifiedNameAsString(), (StringFormatting)i) << "[] = {" << std::endl;
          for (size_t j = 0; j < constants.size(); j++)
          {
            if (j)
            {
              generated_code << ", " << std::endl;
            }
            generated_code << "  \"" << FormatEnumString(constants[j].substr(prefix_length), (StringFormatting)i) << "\"";
          }
          generated_code << std::endl << "};" << std::endl << std::endl;
        }

        const std::string value_array_name = CreateStringArrayVarName(D->getQualifiedNameAsString(), eSF_DIMENSION) + "_values";
        if (!standard_constants)
        {
          includes.insert(filename);
          generated_code << "const " << D->getQualifiedNameAsString() << " " << value_array_name << "[] = {" << std::endl;
          for (size_t i = 0; i < constants.size(); i++)
          {
            if (i)
            {
              generated_code << ", " << std::endl;
            }
            generated_code << "  " << D->getQualifiedNameAsString() << "::" << constants[i];
          }

          /*for (clang::EnumDecl::enumerator_iterator it = enum_decl->enumerator_begin(); count < constants.size(); ++it)
          {
            if (count)
            {
              generated_code << ", " << std::endl;
            }
            generated_code << "  " << (*it)->getInitVal().toString(10);
            count++;
          }*/
          generated_code << std::endl << "};" << std::endl << std::endl;
        }

        generated_code << "const internal::tEnumStrings enum_" << CreateStringArrayVarName(D->getQualifiedNameAsString(), eSF_DIMENSION)
                       << " = { { "
                       << CreateStringArrayVarName(D->getQualifiedNameAsString(), eSF_NATURAL) << ", "
                       << CreateStringArrayVarName(D->getQualifiedNameAsString(), eSF_UPPER) << ", "
                       << CreateStringArrayVarName(D->getQualifiedNameAsString(), eSF_LOWER) << ", "
                       << CreateStringArrayVarName(D->getQualifiedNameAsString(), eSF_CAMEL) << " }, "
                       << constants.size() << ", " << (standard_constants ? std::string("nullptr") : value_array_name) << " };" << std::endl;
        generated_code << "__attribute__ ((init_priority (101))) static internal::tRegisterEnumStrings init_"
                       << CreateStringArrayVarName(D->getQualifiedNameAsString(), eSF_DIMENSION)
                       << "(\"" << D->getQualifiedNameAsString() << "\", enum_"
                       << CreateStringArrayVarName(D->getQualifiedNameAsString(), eSF_DIMENSION) << ");" << std::endl << std::endl;
      }
    }
  }

  virtual void HandleTranslationUnit(clang::ASTContext& Ctx)
  {
    if (Ctx.getDiagnostics().hasErrorOccurred())
    {
      return;
    }

    // Create generated file
    std::ofstream stream(action.output_file.c_str());
    //std::ostream& stream = std::cout;

    // Print header and namespace
    stream << "/*" << std::endl
           << " * This file was generated by enum_strings_builder plugin for llvm-clang" << std::endl
           << " * from the following source files:" << std::endl << " *" << std::endl;
    for (size_t i = 0; i < action.input_files.size(); i++)
    {
      stream << " * " << action.input_files[i] << std::endl;
    }
    stream << " *" << std::endl
           << " * This code is released under the same license as the source files." << std::endl
           << " */" << std::endl << std::endl;

    if (!includes.empty())
    {
      for (std::set<std::string>::const_iterator it = includes.begin(); it != includes.end(); ++it)
      {
        std::string include = *it;
        if (include.compare(0, action.include_dir.length(), action.include_dir) == 0)
        {
          include = include.substr(action.include_dir.length());
        }

        stream << "#include \"" << include << "\"" << std::endl;
      }
      stream << std::endl;
    }

    stream << "namespace make_builder" << std::endl << "{" << std::endl
           << "namespace generated" << std::endl << "{" << std::endl << std::endl;

    // Print content
    stream << generated_code.str();

    // End namespace
    stream << "}" << std::endl  << "}" << std::endl;

    stream.close();
  }
};

#if __clang_major__ == 3 && __clang_minor__ < 6
clang::ASTConsumer *GenerateEnumStringsAction::CreateASTConsumer(clang::CompilerInstance &CI, llvm::StringRef InFile)
{
  return new GenerateEnumStringsConsumer(*this);
}
#else
std::unique_ptr<clang::ASTConsumer> GenerateEnumStringsAction::CreateASTConsumer(clang::CompilerInstance &CI, llvm::StringRef InFile)
{
  return std::unique_ptr<clang::ASTConsumer>(new GenerateEnumStringsConsumer(*this));
}
#endif

static clang::FrontendPluginRegistry::Add<GenerateEnumStringsAction> cREGISTER_PLUGIN("enum-strings", "generate enum strings");
