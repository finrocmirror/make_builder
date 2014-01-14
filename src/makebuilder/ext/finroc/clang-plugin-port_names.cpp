//
// You received this file as part of Finroc
// A framework for intelligent robot control
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
 * \date    2014-01-12
 *
 * Plugin for llvm-clang that generates names for ports whose name is not set
 * in a component constructor.
 */
//----------------------------------------------------------------------

#include <fstream>
#include <sstream>
#include <iostream>
#include <limits>
#include <clang/AST/AST.h>
#include <clang/AST/ASTConsumer.h>
#include <clang/Frontend/FrontendPluginRegistry.h>

class GeneratePortNamesAction : public clang::PluginASTAction
{
public:
  std::string output_file;
  std::vector<std::string> input_files;

  virtual clang::ASTConsumer *CreateASTConsumer(clang::CompilerInstance &CI, llvm::StringRef InFile);

  virtual bool ParseArgs(const clang::CompilerInstance &CI, const std::vector<std::string>& args)
  {
    const std::string OUTPUT_PREFIX = "--output=";
    const std::string INPUTS_PREFIX = "--inputs=";
    for (std::vector<std::string>::const_iterator it = args.begin(); it != args.end(); ++it)
    {
      if ((*it).substr(0, OUTPUT_PREFIX.length())  == OUTPUT_PREFIX)
      {
        output_file = (*it).substr(OUTPUT_PREFIX.length());
      }
      if ((*it).substr(0, INPUTS_PREFIX.length())  == INPUTS_PREFIX)
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

std::string FormatPortNameString(const std::string& s, const std::string& type)
{
  // split string
  std::vector<std::string> words;
  std::stringstream stream(s);
  std::string word;
  while (std::getline(stream, word, '_'))
  {
    words.push_back(word);
  }

  // cut off prefix?
  bool skip_prefix = (words[0] == "si" && type == "tSensorInput") || (words[0] == "so" && type == "tSensorOutput")
                     || (words[0] == "ci" && type == "tControllerInput") || (words[0] == "co" && type == "tControllerOutput")
                     || (words[0] == "par" && type == "tParameter") || (words[0] == "in" && type == "tInput") || (words[0] == "out" && type == "tOutput");
  size_t start_index = skip_prefix ? 1 : 0;

  // transform first letters to upper case
  for (size_t i = 0; i < words.size(); i++)
  {
    words[i][0] = toupper(words[i][0]);
  }

  // join
  std::ostringstream join_stream; // reset stream
  for (size_t i = start_index; i < words.size(); i++)
  {
    if (i > start_index)
    {
      join_stream << " ";
    }
    join_stream << words[i];
  }
  return join_stream.str();
}

/*std::string CreateStringArrayVarName(const std::string& qualified_name, StringFormatting formatting)
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
}*/

class GeneratePortNamesConsumer : public clang::ASTConsumer
{
  clang::ASTContext* ast_context;
  GeneratePortNamesAction& action;
  std::ostringstream generated_code;

public:

  GeneratePortNamesConsumer(GeneratePortNamesAction& action) :
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
    /*for (clang::DeclGroupRef::iterator i = DG.begin(), e = DG.end(); i != e; ++i)
    {
      const clang::Decl *D = *i;
      //if (const clang::NamedDecl *ND = dynamic_cast<const clang::NamedDecl*>(D))
      //llvm::outs() << "top-level-decl: \"" << ND->getNameAsString() << "\"\n";
    }*/

    return true;
  }

  virtual void HandleInterestingDecl(clang::DeclGroupRef DG)
  {
    /*for (clang::DeclGroupRef::iterator i = DG.begin(), e = DG.end(); i != e; ++i)
    {
      //const clang::Decl *D = *i;
      //if (const clang::NamedDecl *ND = dynamic_cast<const clang::NamedDecl*>(D))
      //llvm::outs() << "top-level-decl: \"" << ND->getNameAsString() << "\"\n";
      (*i)->print(llvm::outs());
    }*/
  }

  virtual void HandleTagDeclDefinition(clang::TagDecl *D)
  {
    //const clang::Decl *D = *i;
    //if (const clang::NamedDecl *ND = dynamic_cast<const clang::NamedDecl*>(D))
    //llvm::outs() << "tag-level-decl: \"" << D->getNameAsString() << "\"\n";
    if (D->isClass() && D->getName().size() > 1 && (D->getName().front() == 'm' || D->getName().front() == 'g'))
    {
      std::string class_name = D->getNameAsString();
      if ((!isupper(class_name[1])) && (!(class_name[1] == 'b' && class_name[2] == 'b')))
      {
        return;
      }

      //D->getOuterLocStart().print(llvm::outs(), ast_context->getSourceManager());
      std::string filename;
      if (D->getOuterLocStart().isFileID())
      {
        filename = ast_context->getSourceManager().getFilename(D->getOuterLocStart());
      }
      clang::RecordDecl* class_decl = dynamic_cast<clang::RecordDecl*>(D);
      if (class_decl && filename.length() > 0 && filename[0] != '/' &&
          std::find(action.input_files.begin(), action.input_files.end(), filename) != action.input_files.end())
      {
        //llvm::outs() << "\nNext Enum: " << filename << " " << D->getQualifiedNameAsString() << "\n";
        //D->print(llvm::outs());
        //llvm::outs() << "\n";

        generated_code << "  // class " << D->getQualifiedNameAsString() << std::endl;
        generated_code << "  names.clear();" << std::endl;

        // Generate code
        for (clang::RecordDecl::field_iterator it = class_decl->field_begin(); it != class_decl->field_end(); ++it)
        {
          std::string type_name = (*it)->getType().getAsString();
          if (type_name.find('<') != std::string::npos)
          {
            type_name = type_name.substr(0, type_name.find('<'));
            if (type_name == "tInput" || type_name == "tOutput" || type_name == "tControllerInput" || type_name == "tControllerOutput" || type_name == "tSensorInput" || type_name == "tSensorOutput" || type_name == "tParameter" || type_name == "tStaticParameter")
            {
              generated_code << "  names.push_back(\"" << FormatPortNameString((*it)->getNameAsString(), type_name) << "\");" << std::endl;
            }
          }

          /*if ((*it)->getType()

          generated_code << "const char* " << CreateStringArrayVarName(D->getQualifiedNameAsString(), (StringFormatting)i) << "[] = {" << std::endl;
          for (size_t j = 0; j < constants.size(); j++)
          {
            if (j)
            {
              generated_code << ", " << std::endl;
            }
            generated_code << "  \"" << FormatEnumString(constants[j].substr(prefix_length), (StringFormatting)i) << "\"";
          }
          generated_code << std::endl << "};" << std::endl << std::endl;*/
        }

        generated_code << "  AddPortNamesForModuleType(\"" + D->getQualifiedNameAsString() + "\", names);" << std::endl << std::endl;
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

    // Print header, namespace and start of init function
    stream << "/*" << std::endl
           << " * This file was generated by finroc_port_name_builder plugin for llvm-clang" << std::endl
           << " * from the following source files:" << std::endl << " *" << std::endl;
    for (size_t i = 0; i < action.input_files.size(); i++)
    {
      stream << " * " << action.input_files[i] << std::endl;
    }
    stream << " *" << std::endl
           << " * This code is released under the same license as the source files." << std::endl
           << " */" << std::endl  << std::endl
           << "#include \"plugins/structure/internal/register.h\"" << std::endl  << std::endl
           << "using namespace finroc::structure::internal;" << std::endl  << std::endl
           << "namespace finroc" << std::endl << "{" << std::endl
           << "namespace generated" << std::endl << "{" << std::endl << std::endl
           << "static int InitializePortNames()" << std::endl << "{" << std::endl
           << "  std::vector<std::string> names;" << std::endl << std::endl;

    // Print content
    stream << generated_code.str();

    // End file
    stream << "  return 0;" << std::endl << "}" << std::endl << std::endl
           << "__attribute__((unused)) static int cINIT = InitializePortNames();" << std::endl << std::endl
           << "}" << std::endl  << "}" << std::endl;

    stream.close();
  }
};

clang::ASTConsumer* GeneratePortNamesAction::CreateASTConsumer(clang::CompilerInstance &CI, llvm::StringRef InFile)
{
  return new GeneratePortNamesConsumer(*this);
}

static clang::FrontendPluginRegistry::Add<GeneratePortNamesAction> cREGISTER_PLUGIN("finroc_port_names", "generate finroc port names");
